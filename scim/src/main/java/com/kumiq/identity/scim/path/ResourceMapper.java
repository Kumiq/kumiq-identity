package com.kumiq.identity.scim.path;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Weinan Qiu
 * @since 1.0.0
 */
public class ResourceMapper {

    private final MappingContext context;
    private final Configuration configuration;

    public static Map<String, Object> convertToMap(MappingContext context, Configuration configuration) {
        return new ResourceMapper(context, configuration).doConvert();
    }

    public ResourceMapper(MappingContext context, Configuration configuration) {
        this.context = context;
        this.configuration = configuration;
    }

    /**
     * Converts the given object to Map, with the help of schema and path inclusion / exclusion hints.
     *
     * @return
     */
    Map<String, Object> doConvert() {
        Map<String, Object> resultMap = new HashMap<>();

        for (String pathToInclude : context.getIncludePaths()) {
            CompilationContext compilationContext = CompilationContext
                    .create(pathToInclude, context.getData())
                    .withSchema(context.getSchema());
            Configuration compilationConfig = configuration.clone().withOption(Configuration.Option.COMPILE_WITH_HINT);
            PathCompiler.compile(compilationContext, compilationConfig).stream().forEach(pathRef -> {
                EvaluationContext evaluationContext = new EvaluationContext(context.getData());
                evaluationContext = pathRef.evaluate(evaluationContext, configuration);
                Object evaluationResult = evaluationContext.getCursor();

                ModificationUnit modificationUnit = new ModificationUnit(ModificationUnit.Operation.ADD, pathToInclude, evaluationResult);
                ModificationContext modificationContext = new ModificationContext(modificationUnit, context.getSchema(), resultMap);
                Modifier.create(pathRef, modificationContext, Configuration.withMapObjectProvider()).modify();
            });
        }

        for (String pathToExclude : context.getExcludePaths()) {
            CompilationContext compilationContext = CompilationContext
                    .create(pathToExclude, context.getData())
                    .withSchema(context.getSchema());
            Configuration compilationConfig = configuration.clone().withOption(Configuration.Option.COMPILE_WITH_HINT);
            PathCompiler.compile(compilationContext, compilationConfig).stream().forEach(pathRef -> {
                ModificationUnit modificationUnit = new ModificationUnit(ModificationUnit.Operation.REMOVE, pathToExclude, null);
                ModificationContext modificationContext = new ModificationContext(modificationUnit, context.getSchema(), resultMap);
                Modifier.create(pathRef, modificationContext, Configuration.withMapObjectProvider()).modify();
            });
        }

        return resultMap;
    }
}
