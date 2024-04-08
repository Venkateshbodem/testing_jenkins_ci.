package org.gradle.internal.declarativedsl.analysis

import org.gradle.internal.declarativedsl.language.Block
import org.gradle.internal.declarativedsl.language.Import


interface Resolver {
    fun resolve(schema: AnalysisSchema, imports: List<Import>, topLevelBlock: Block): ResolutionResult
}


class ResolverImpl(
    private val codeAnalyzer: CodeAnalyzer,
    private val errorCollector: ErrorCollector
) : Resolver {
    override fun resolve(schema: AnalysisSchema, imports: List<Import>, topLevelBlock: Block): ResolutionResult {
        val importContext = AnalysisContext(schema, emptyMap(), errorCollector)
        val importFqnBySimpleName = collectImports(imports, importContext) + schema.defaultImports.associateBy { it.simpleName }

        val topLevelReceiver = ObjectOrigin.TopLevelReceiver(schema.topLevelReceiverType as DataClassImpl, topLevelBlock)
        val topLevelScope = AnalysisScope(null, topLevelReceiver, topLevelBlock)

        val context = AnalysisContext(schema, importFqnBySimpleName, errorCollector)
        context.withScope(topLevelScope) { codeAnalyzer.analyzeStatementsInProgramOrder(context, topLevelBlock.statements) }

        return ResolutionResult(topLevelReceiver, context.assignments, context.additions, errorCollector.errors)
    }

    fun collectImports(
        trees: List<Import>,
        analysisContext: AnalysisContext
    ): Map<String, FqName> = buildMap {
        trees.forEach { import ->
            val fqn = FqNameImpl(
                import.name.nameParts.dropLast(1).joinToString("."), import.name.nameParts.last()
            )

            compute(fqn.simpleName) { _, existing ->
                if (existing != null && existing != fqn) {
                    analysisContext.errorCollector.collect(ResolutionError(import, ErrorReason.AmbiguousImport(fqn)))
                    existing
                } else {
                    fqn
                }
            }
        }
    }
}


/**
 * The only purpose of this class is to expose the resolution [trace] to the consumers.
 */
class TracingResolver(
    private val resolver: Resolver,
    val trace: ResolutionTrace
) : Resolver {
    override fun resolve(schema: AnalysisSchema, imports: List<Import>, topLevelBlock: Block): ResolutionResult = resolver.resolve(schema, imports, topLevelBlock)
}
