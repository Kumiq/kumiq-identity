package com.kumiq.identity.scim.path;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static com.kumiq.identity.scim.utils.TypeUtils.*;

/**
 * Compiler for a SCIM path. Examples:
 * - foo.bar
 * - foo.bar[value eq 100].foobar
 *
 * @author Weinan Qiu
 * @since 1.0.0
 */
public class PathCompiler {

    /**
     * Compile the SCIM path and return the head for the token list. The
     * list shall only contain {@link SimplePathToken} and {@link PathWithIndexToken}
     *
     * @param path SCIM path
     * @param data the object used for index evaluation
     * @return head of the path token tree
     */
    List<PathToken> compile(String path, Map<String, Object> data) {
        Tokenizer pathTokenizer = new Tokenizer.PathTokenizer(path);
        List<PathToken> tokens = new ArrayList<>();
        tokens.add(PathTokenFactory.root());

        while (true) {
            try {
                String tokenValue = pathTokenizer.nextSequence().toString();
                PathToken token = containsFilter(tokenValue) ?
                        PathTokenFactory.pathWithFilter(tokenValue) :
                        PathTokenFactory.simplePathToken(tokenValue);
                tokens.add(token);
            } catch (Tokenizer.NoMoreSequenceException ex) {
                break;
            }
        }
        PathToken.linkTokenList(tokens);

        PathToken pathRoot = tokens.get(0);
        traverseAndReplace(pathRoot, data);

        return null;
    }

    /**
     * In all possible paths of the tree, search for {@link PathWithFilterToken} and replace it with a list
     * of {@link PathWithIndexToken}. In the event when replacement happens, restart the search from the beginning
     * to maintain correct results as context has changed.
     *
     * @param root
     */
    private void traverseAndReplace(PathToken root, Map<String, Object> data) {
        boolean acquireNewPaths = true;
        do {
            List<List<PathToken>> paths = new ArrayList<>();
            root.traverse(paths);
            paths.forEach(PathToken::linkTokenList);

            for (List<PathToken> path : paths) {
                Optional<PathWithFilterToken> result = findFirstPathWithReferenceToken(path.get(0));
                if (result.isPresent()) {
                    PathWithFilterToken tokenToReplace = result.get();
                    List<PathWithIndexToken> replacementTokens = resolvePathWithFilterToken(tokenToReplace, data);
                    if (CollectionUtils.isEmpty(replacementTokens)) {
                        throw new FilterTokenResolvedToNothingException(tokenToReplace);
                    }

                    PathToken prev = tokenToReplace.getPrev();
                    prev.replaceTokens(tokenToReplace, replacementTokens);
                    prev.getNext().forEach(PathToken::replaceDownstreamWithClones);

                    acquireNewPaths = true;
                    break;
                } else {
                    acquireNewPaths = false;
                }
            }

        } while (acquireNewPaths);
    }

    private Optional<PathWithFilterToken> findFirstPathWithReferenceToken(PathToken root) {
        PathToken cursor = root;
        while (cursor != null) {
            if (cursor instanceof PathWithFilterToken)
                return Optional.of((PathWithFilterToken) cursor);
            cursor = cursor.firstNext();
        }
        return Optional.empty();
    }

    private List<PathWithIndexToken> resolvePathWithFilterToken(PathWithFilterToken token, Map<String, Object> data) {
        PathToken clonedRoot = token.getPrev().clonedSubListWithSelfAsLeaf();

        Optional<PathWithFilterToken> result = findFirstPathWithReferenceToken(clonedRoot);
        Assert.isTrue(!result.isPresent(), "Cloned path cannot contain another token with filter before the one supplied to evaluate.");

        Object value = clonedRoot.evaluate(data, data);
        Assert.isTrue(isList(value), "evaluation didn't result in list.");
        //asList(value).stream().filter()

        return new ArrayList<>();
    }

    private boolean containsFilter(String token) {
        return token.contains("[") && token.endsWith("]");
    }

    /**
     * Exception thrown when a {@link PathWithFilterToken} didn't resolve to any {@link PathWithIndexToken}, indicating
     * nothing can be supplied for downstream tokens. Callers may decide what to do with the exception.
     */
    public static class FilterTokenResolvedToNothingException extends RuntimeException {

        private final PathWithFilterToken token;

        FilterTokenResolvedToNothingException(PathWithFilterToken token) {
            this.token = token;
        }

        public PathWithFilterToken getToken() {
            return token;
        }
    }
}