/*
 * Copyright (c) 2021, Fraunhofer AISEC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *                    $$$$$$\  $$$$$$$\   $$$$$$\
 *                   $$  __$$\ $$  __$$\ $$  __$$\
 *                   $$ /  \__|$$ |  $$ |$$ /  \__|
 *                   $$ |      $$$$$$$  |$$ |$$$$\
 *                   $$ |      $$  ____/ $$ |\_$$ |
 *                   $$ |  $$\ $$ |      $$ |  $$ |
 *                   \$$$$$   |$$ |      \$$$$$   |
 *                    \______/ \__|       \______/
 *
 */
package de.fraunhofer.aisec.cpg.backends.llvm

import de.fraunhofer.aisec.cpg.backends.LanguageBackend
import de.fraunhofer.aisec.cpg.graph.HasType
import de.fraunhofer.aisec.cpg.graph.declarations.FunctionDeclaration
import de.fraunhofer.aisec.cpg.graph.declarations.TranslationUnitDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.CompoundStatement
import de.fraunhofer.aisec.cpg.graph.statements.ReturnStatement
import de.fraunhofer.aisec.cpg.graph.statements.Statement
import de.fraunhofer.aisec.cpg.graph.types.IncompleteType
import org.bytedeco.llvm.LLVM.*
import org.bytedeco.llvm.global.LLVM.*

class LLVMIRLanguageBackend : LanguageBackend<LLVMTypeRef>() {
    lateinit var ctx: LLVMContextRef
    lateinit var builder: LLVMBuilderRef

    override fun generate(tu: TranslationUnitDeclaration) {
        ctx = LLVMContextCreate()
        builder = LLVMCreateBuilderInContext(ctx)

        var mod = LLVMModuleCreateWithName(tu.name)

        // LLVMPrintModuleToFile
        for (func in tu.declarations.filterIsInstance<FunctionDeclaration>()) {
            // check, if it is only a declaration for an existing definition and skip it
            if (!func.isDefinition && func.definition != null) {
                continue
            }

            generateFunction(mod, func)
        }

        println(LLVMPrintModuleToString(mod).string)
    }

    private fun generateFunction(mod: LLVMModuleRef, func: FunctionDeclaration) {
        val returnType = this.typeOf(func)
        val functionType = LLVMFunctionType(returnType, LLVMTypeRef(), 0, 0)

        var valueRef = LLVMAddFunction(mod, func.name, functionType)

        func.body?.let {
            // handle the function body
            generateCompoundStatement(valueRef, it as CompoundStatement, "entry")
        }
    }

    private fun generateCompoundStatement(
        func: LLVMValueRef,
        comp: CompoundStatement,
        name: String
    ) {
        val bb = LLVMAppendBasicBlockInContext(ctx, func, name)

        LLVMPositionBuilderAtEnd(builder, bb)

        for (stmt in comp.statements) {
            generateStatement(stmt)
        }
    }

    private fun generateStatement(stmt: Statement) {
        if (stmt is ReturnStatement) {
            generateReturnStatement(stmt)
        }
    }

    private fun generateReturnStatement(returnStatement: ReturnStatement): LLVMValueRef {
        val valueRef = LLVMBuildRetVoid(builder)

        // LLVMInsertIntoBuilder(builder, valueRef)

        return valueRef
    }

    override fun typeOf(node: HasType): LLVMTypeRef {
        return if (node.type is IncompleteType && node.type.name == "void") {
            LLVMVoidType()
        } else {
            LLVMIntType(64)
        }
    }
}
