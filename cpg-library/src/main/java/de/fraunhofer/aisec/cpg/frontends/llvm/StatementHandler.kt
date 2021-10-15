/*
 * Copyright (c) 2019 - 2020, Fraunhofer AISEC. All rights reserved.
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
package de.fraunhofer.aisec.cpg.frontends.llvm

import de.fraunhofer.aisec.cpg.frontends.Handler
import de.fraunhofer.aisec.cpg.graph.NodeBuilder.*
import de.fraunhofer.aisec.cpg.graph.declarations.VariableDeclaration
import de.fraunhofer.aisec.cpg.graph.statements.CompoundStatement
import de.fraunhofer.aisec.cpg.graph.statements.DeclarationStatement
import de.fraunhofer.aisec.cpg.graph.statements.Statement
import de.fraunhofer.aisec.cpg.graph.statements.expressions.*
import de.fraunhofer.aisec.cpg.graph.types.*
import java.util.*
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.llvm.LLVM.LLVMBasicBlockRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM.*

class StatementHandler(lang: LLVMIRLanguageFrontend) :
    Handler<Statement, Pointer, LLVMIRLanguageFrontend>(::Statement, lang) {
    init {
        map.put(LLVMValueRef::class.java) { handleInstruction(it as LLVMValueRef) }
        map.put(LLVMBasicBlockRef::class.java) { handleBasicBlock(it as LLVMBasicBlockRef) }
    }

    /**
     * Handles the parsing of
     * [instructions](https://llvm.org/docs/LangRef.html#instruction-reference). Instructions are
     * usually mapped to statements.
     *
     * It is noteworthy, that LLVM IR is a single state assignment form, meaning, that all
     * instructions that perform an assignment will result in a [DeclarationStatement] and a
     * [VariableDeclaration], with the original instruction wrapped into the
     * [VariableDeclaration.initializer] property.
     *
     * Currently, this wrapping is done in the individual instruction parsing functions, but should
     * be extracted from that, e.g. by routing it through the [DeclarationHandler].
     */
    private fun handleInstruction(instr: LLVMValueRef): Statement {
        val opcode = instr.opCode

        when (opcode) {
            LLVMRet -> {
                val ret = newReturnStatement(lang.getCodeFromRawNode(instr))

                val numOps = LLVMGetNumOperands(instr)
                if (numOps != 0) {
                    ret.returnValue = getOperandValueAtIndex(instr, 0)
                }

                return ret
            }
            LLVMBr -> {
                return handleBrStatement(instr)
            }
            LLVMSwitch -> {
                return handleSwitchStatement(instr)
            }
            LLVMIndirectBr -> {
                println("indirect br instruction")
            }
            LLVMInvoke -> {
                // TODO: Function call and an edge potentially transferring control flow to a catch
                println("invoke instruction")
            }
            LLVMUnreachable -> {
                // Does nothing
                return Statement()
            }
            LLVMCallBr -> {
                // TODO: Maps to a call but also to a goto statement?
                println("call instruction")
            }
            LLVMFNeg -> {
                val fneg = newUnaryOperator("-", false, true, lang.getCodeFromRawNode(instr))
                fneg.input = getOperandValueAtIndex(instr, 0)
                return fneg
            }
            LLVMAdd -> {
                return handleBinaryOperator(instr, "+", false)
            }
            LLVMFAdd -> {
                return handleBinaryOperator(instr, "+", false)
            }
            LLVMSub -> {
                return handleBinaryOperator(instr, "-", false)
            }
            LLVMFSub -> {
                return handleBinaryOperator(instr, "-", false)
            }
            LLVMMul -> {
                return handleBinaryOperator(instr, "*", false)
            }
            LLVMFMul -> {
                return handleBinaryOperator(instr, "*", false)
            }
            LLVMUDiv -> {
                return handleBinaryOperator(instr, "+", true)
            }
            LLVMSDiv -> {
                return handleBinaryOperator(instr, "/", false)
            }
            LLVMFDiv -> {
                return handleBinaryOperator(instr, "/", false)
            }
            LLVMURem -> {
                return handleBinaryOperator(instr, "%", true)
            }
            LLVMSRem -> {
                // TODO: This is not 100% accurate and needs to be handled later or needs a new
                // operator
                return handleBinaryOperator(instr, "%", false)
            }
            LLVMFRem -> {
                return handleBinaryOperator(instr, "%", false)
            }
            LLVMShl -> {
                return handleBinaryOperator(instr, "<<", false)
            }
            LLVMLShr -> {
                return handleBinaryOperator(instr, ">>", false)
            }
            LLVMAShr -> {
                // TODO: This is not 100% accurate and needs to be handled later or needs a new
                // operator
                return handleBinaryOperator(instr, ">>", true)
            }
            LLVMAnd -> {
                return handleBinaryOperator(instr, "&", false)
            }
            LLVMOr -> {
                return handleBinaryOperator(instr, "|", false)
            }
            LLVMXor -> {
                return handleBinaryOperator(instr, "^", false)
            }
            LLVMAlloca -> {
                return handleAlloca(instr)
            }
            LLVMLoad -> {
                return handleLoad(instr)
            }
            LLVMStore -> {
                return handleStore(instr)
            }
            LLVMGetElementPtr -> {
                return handleGetElementPtr(instr)
            }
            LLVMTrunc -> {
                println("trunc instruction")
            }
            LLVMZExt -> {
                println("zext instruction")
            }
            LLVMSExt -> {
                println("sext instruction")
            }
            LLVMFPToUI -> {
                println("fptoui instruction")
            }
            LLVMFPToSI -> {
                println("fptosi instruction")
            }
            LLVMUIToFP -> {
                println("uitofp instruction")
            }
            LLVMSIToFP -> {
                println("sitofp instruction")
            }
            LLVMFPTrunc -> {
                println("fptrunc instruction")
            }
            LLVMFPExt -> {
                println("fpext instruction")
            }
            LLVMPtrToInt -> {
                println("ptrtoint instruction")
            }
            LLVMIntToPtr -> {
                println("inttoptr instruction")
            }
            LLVMBitCast -> {
                println("bitcast instruction")
            }
            LLVMAddrSpaceCast -> {
                println("addrspacecast instruction")
            }
            LLVMICmp -> {
                return handleIntegerComparison(instr)
            }
            LLVMFCmp -> {
                return handleFloatComparison(instr)
            }
            LLVMPHI -> {
                println("phi instruction")
            }
            LLVMCall -> {
                return parseFunctionCall(instr)
            }
            LLVMSelect -> {
                return handleSelect(instr)
            }
            LLVMUserOp1 -> {
                println("userop1 instruction")
            }
            LLVMUserOp2 -> {
                println("userop2 instruction")
            }
            LLVMVAArg -> {
                println("va_arg instruction")
            }
            LLVMExtractElement -> {
                println("extractelement instruction")
            }
            LLVMInsertElement -> {
                println("insertelement instruction")
            }
            LLVMShuffleVector -> {
                println("shufflevector instruction")
            }
            LLVMExtractValue -> {
                return handleGetElementPtr(instr)
            }
            LLVMInsertValue -> {
                return handleInsertValue(instr)
            }
            LLVMFreeze -> {
                println("freeze instruction")
            }
            LLVMFence -> {
                println("fence instruction")
            }
            LLVMAtomicCmpXchg -> {
                return handleAtomiccmpxchg(instr)
            }
            LLVMAtomicRMW -> {
                return handleAtomicrmw(instr)
            }
            LLVMResume -> {
                println("resume instruction")
            }
            LLVMLandingPad -> {
                println("landingpad instruction")
            }
            LLVMCleanupRet -> {
                println("cleanupret instruction")
            }
            LLVMCatchRet -> {
                println("catchret instruction")
            }
            LLVMCatchPad -> {
                println("catchpad instruction")
            }
            LLVMCleanupPad -> {
                println("cleanuppad instruction")
            }
            LLVMCatchSwitch -> {
                println("catchswitch instruction")
            }
        }

        log.error("Not handling instruction opcode {} yet", opcode)
        return Statement()
    }

    /**
     * Handles the ['alloca'](https://llvm.org/docs/LangRef.html#alloca-instruction) instruction,
     * which allocates a defined block of memory. The closest what we have in the graph is the
     * [ArrayCreationExpression], which creates a fixed sized array, i.e., a block of memory.
     */
    private fun handleAlloca(instr: LLVMValueRef): Statement {
        val array = newArrayCreationExpression(lang.getCodeFromRawNode(instr))

        array.type = lang.typeOf(instr)

        // LLVM is quite forthcoming here. in case the optional length parameter is omitted in the
        // source code, it will automatically be set to 1
        val size = getOperandValueAtIndex(instr, 0)

        array.addDimension(size)

        return declarationOrNot(array, instr)
    }

    /**
     * Handles the [`store`](https://llvm.org/docs/LangRef.html#store-instruction) instruction. It
     * stores a particular value at a pointer address. This is the rough equivalent to an assignment
     * of a de-referenced pointer in C like `*a = 1`.
     */
    private fun handleStore(instr: LLVMValueRef): Statement {
        val binOp = newBinaryOperator("=", lang.getCodeFromRawNode(instr))

        val dereference = newUnaryOperator("*", false, true, "")
        dereference.input = getOperandValueAtIndex(instr, 1)

        binOp.lhs = dereference
        binOp.rhs = getOperandValueAtIndex(instr, 0)

        return binOp
    }

    /**
     * Handles the [`load`](https://llvm.org/docs/LangRef.html#load-instruction) instruction, which
     * is basically just a pointer de-reference.
     */
    private fun handleLoad(instr: LLVMValueRef): Statement {
        val ref = newUnaryOperator("*", false, true, "")
        ref.input = getOperandValueAtIndex(instr, 0)

        return declarationOrNot(ref, instr)
    }

    /**
     * Handles the [`icmp`](https://llvm.org/docs/LangRef.html#icmp-instruction) instruction for
     * comparing integer values.
     */
    private fun handleIntegerComparison(instr: LLVMValueRef): Statement {
        var unsigned = false
        val cmpPred =
            when (LLVMGetICmpPredicate(instr)) {
                LLVMIntEQ -> "=="
                LLVMIntNE -> "!="
                LLVMIntUGT -> {
                    unsigned = true
                    ">"
                }
                LLVMIntUGE -> {
                    unsigned = true
                    ">="
                }
                LLVMIntULT -> {
                    unsigned = true
                    "<"
                }
                LLVMIntULE -> {
                    unsigned = true
                    "<="
                }
                LLVMIntSGT -> ">"
                LLVMIntSGE -> ">="
                LLVMIntSLT -> "<"
                LLVMIntSLE -> "<="
                else -> "unknown"
            }

        return handleBinaryOperator(instr, cmpPred, unsigned)
    }

    /**
     * Handles the [`fcmp`](https://llvm.org/docs/LangRef.html#fcmp-instruction) instruction for
     * comparing floating point values.
     */
    private fun handleFloatComparison(instr: LLVMValueRef): Statement {
        var unordered = false
        val cmpPred =
            when (LLVMGetFCmpPredicate(instr)) {
                LLVMRealPredicateFalse -> {
                    return newLiteral(false, TypeParser.createFrom("i1", true), "false")
                }
                LLVMRealOEQ -> "=="
                LLVMRealOGT -> ">"
                LLVMRealOGE -> ">="
                LLVMRealOLT -> "<"
                LLVMRealOLE -> "<="
                LLVMRealONE -> "!="
                LLVMRealORD -> "ord"
                LLVMRealUNO -> "uno"
                LLVMRealUEQ -> {
                    unordered = true
                    "=="
                }
                LLVMRealUGT -> {
                    unordered = true
                    ">"
                }
                LLVMRealUGE -> {
                    unordered = true
                    ">="
                }
                LLVMRealULT -> {
                    unordered = true
                    "<"
                }
                LLVMRealULE -> {
                    unordered = true
                    "<="
                }
                LLVMRealUNE -> {
                    unordered = true
                    "!="
                }
                LLVMRealPredicateTrue -> {
                    return newLiteral(true, TypeParser.createFrom("i1", true), "true")
                }
                else -> "unknown"
            }

        return handleBinaryOperator(instr, cmpPred, false, unordered)
    }

    /**
     * Handles the [`getelementptr`](https://llvm.org/docs/LangRef.html#getelementptr-instruction)
     * instruction and the
     * [`extractvalue`](https://llvm.org/docs/LangRef.html#extractvalue-instruction) instruction
     * which works in a similar way.
     *
     * We try to convert it either into an [ArraySubscriptionExpression] or an [MemberExpression],
     * depending whether the accessed variable is a struct or an array. Furthermore, since
     * `getelementptr` allows an (infinite) chain of sub-element access within a single instruction,
     * we need to unwrap those into individual expressions.
     */
    internal fun handleGetElementPtr(instr: LLVMValueRef): Statement {
        val isGetElementPtr = LLVMGetInstructionOpcode(instr) == LLVMGetElementPtr

        val numOps: Int
        val loopStart: Int
        var indices = IntPointer()

        if (isGetElementPtr) {
            numOps = LLVMGetNumOperands(instr)
            loopStart = 1
        } else {
            numOps = LLVMGetNumIndices(instr)
            loopStart = 0
            indices = LLVMGetIndices(instr)
        }

        // the first operand is always type that is the basis for the calculation
        var baseType = lang.typeOf(LLVMGetOperand(instr, 0))
        var operand = getOperandValueAtIndex(instr, 0)

        // the start
        var base = operand

        var expr = Expression()

        // loop through all operands / indices
        for (idx: Int in loopStart until numOps) {
            val index =
                if (isGetElementPtr) {
                    // the second argument is the base address that we start our chain from
                    operand = getOperandValueAtIndex(instr, idx)

                    // Parse index as int for now only
                    ((operand as Literal<*>).value as Long).toInt()
                } else {
                    indices.get(idx.toLong())
                }

            // check, if the current base type is a pointer -> then we need to handle this as an
            // array access
            if (baseType is PointerType) {
                val arrayExpr = newArraySubscriptionExpression("")
                arrayExpr.arrayExpression = base
                arrayExpr.name = index.toString()
                arrayExpr.subscriptExpression = operand
                expr = arrayExpr

                log.info("{}", expr)

                // deference the type to get the new base type
                baseType = baseType.dereference()

                // the current expression is the new base
                base = expr
            } else {
                // otherwise, this is a member field access, where the index denotes the n-th field
                // in the structure
                val record = (baseType as? ObjectType)?.recordDeclaration

                // this should not happen at this point, we cannot continue
                if (record == null) {
                    log.error(
                        "Could not find structure type with name {}, cannot continue",
                        baseType.typeName
                    )
                    break
                }

                log.debug(
                    "Trying to access a field within the record declaration of {}",
                    record.name
                )

                // look for the field
                val field = record.getField("field_$index")

                // our new base-type is the type of the field
                baseType = field?.type ?: UnknownType.getUnknownType()

                // construct our member expression
                expr = newMemberExpression(base, field?.type, field?.name, ".", "")
                log.info("{}", expr)

                // the current expression is the new base
                base = expr
            }
        }

        // since getelementpr returns the *address*, whereas extractvalue returns a *value*, we need
        // to do a final unary & operation
        if (isGetElementPtr) {
            val ref = newUnaryOperator("&", false, true, "")
            ref.input = expr
            expr = ref
        }

        return declarationOrNot(expr, instr)
    }

    /**
     * Handles the [`insertvalue`](https://llvm.org/docs/LangRef.html#insertvalue-instruction)
     * instruction.
     *
     * We use it similar to a constructor and assign the individual sub-elements.
     */
    private fun handleInsertValue(instr: LLVMValueRef): Statement {
        val numOps = LLVMGetNumIndices(instr)
        val indices = LLVMGetIndices(instr)

        var baseType = lang.typeOf(LLVMGetOperand(instr, 0))
        val operand = getOperandValueAtIndex(instr, 0)
        val valueToSet = getOperandValueAtIndex(instr, 1)

        var base = operand

        // Make a copy of the operand
        var copy = Statement()
        if (operand !is ConstructExpression) {
            copy = declarationOrNot(operand, instr)
            if (copy is DeclarationStatement) {
                base =
                    newDeclaredReferenceExpression(
                        copy.singleDeclaration.name,
                        (copy.singleDeclaration as VariableDeclaration).type,
                        lang.getCodeFromRawNode(instr)
                    )
            }
        }
        var expr: Expression

        for (idx: Int in 0 until numOps) {
            val index = indices.get(idx.toLong())

            if (base is ConstructExpression) {
                if (idx == numOps - 1) {
                    base.setArgument(index, valueToSet)
                    return declarationOrNot(operand, instr)
                }
                base = base.arguments[index]
            } else {
                // otherwise, this is a member field access, where the index denotes the n-th field
                // in the structure
                val record = (baseType as? ObjectType)?.recordDeclaration

                // this should not happen at this point, we cannot continue
                if (record == null) {
                    log.error(
                        "Could not find structure type with name {}, cannot continue",
                        baseType.typeName
                    )
                    break
                }

                log.debug(
                    "Trying to access a field within the record declaration of {}",
                    record.name
                )

                // look for the field
                val field = record.getField("field_$index")

                // our new base-type is the type of the field
                baseType = field?.type ?: UnknownType.getUnknownType()

                // construct our member expression
                expr = newMemberExpression(base, field?.type, field?.name, ".", "")
                log.info("{}", expr)

                // the current expression is the new base
                base = expr
            }
        }

        val compoundStatement = newCompoundStatement(lang.getCodeFromRawNode(instr))

        val assignment = newBinaryOperator("=", lang.getCodeFromRawNode(instr))
        assignment.lhs = base
        assignment.rhs = valueToSet
        compoundStatement.addStatement(copy)
        compoundStatement.addStatement(assignment)

        return compoundStatement
    }

    /**
     * Parses the [`atomicrmw`](https://llvm.org/docs/LangRef.html#atomicrmw-instruction)
     * instruction. It returns either a single [Statement] or a [CompoundStatement] if the value is
     * assigned to another variable. Performs the following operation atomically:
     * ```
     * lhs = {*pointer, *pointer == cmp} // A struct of {T, i1}
     * if(*pointer == cmp) { *pointer = new }
     * ```
     * Returns a [CompoundStatement] with those two instructions or, if `lhs` doesn't exist, only
     * the if-then statement.
     */
    private fun handleAtomiccmpxchg(instr: LLVMValueRef): Statement {
        val instrStr = lang.getCodeFromRawNode(instr)
        val compoundStatement = newCompoundStatement(instrStr)
        compoundStatement.name = "atomiccmpxchg"
        val ptr = getOperandValueAtIndex(instr, 0)
        val cmp = getOperandValueAtIndex(instr, 1)
        val value = getOperandValueAtIndex(instr, 2)

        val ptrDeref = newUnaryOperator("*", false, true, instrStr)
        ptrDeref.input = ptr

        val cmpExpr = newBinaryOperator("==", instrStr)
        cmpExpr.lhs = ptrDeref
        cmpExpr.rhs = cmp

        val lhs = LLVMGetValueName(instr).string
        if (lhs != "") {
            // we need to create a crazy struct here. the target type can be found here
            val targetType = lang.typeOf(instr)

            // construct it
            val construct = newConstructExpression("")
            construct.instantiates = (targetType as? ObjectType)?.recordDeclaration

            construct.addArgument(ptrDeref)
            construct.addArgument(cmpExpr)

            val decl = declarationOrNot(construct, instr)
            compoundStatement.addStatement(decl)
        }
        val assignment = newBinaryOperator("=", instrStr)
        assignment.lhs = ptrDeref
        assignment.rhs = value

        val ifStatement = newIfStatement(instrStr)
        ifStatement.condition = cmpExpr
        ifStatement.thenStatement = assignment

        compoundStatement.addStatement(ifStatement)

        return compoundStatement
    }

    /**
     * Parses the `atomicrmw` instruction. It returns either a single [Statement] or a
     * [CompoundStatement] if the value is assigned to another variable. >>>>>>> Start with cmpxchg
     * instruction
     */
    private fun handleAtomicrmw(instr: LLVMValueRef): Statement {
        val lhs = LLVMGetValueName(instr).string
        val instrStr = lang.getCodeFromRawNode(instr)
        val operation = LLVMGetAtomicRMWBinOp(instr)
        val ptr = getOperandValueAtIndex(instr, 0)
        val value = getOperandValueAtIndex(instr, 1)
        val ty = value.type
        val exchOp = newBinaryOperator("=", instrStr)
        exchOp.name = "atomicrmw"

        val ptrDeref = newUnaryOperator("*", false, true, instrStr)
        ptrDeref.input = ptr
        exchOp.lhs = ptrDeref

        when (operation) {
            LLVMAtomicRMWBinOpXchg -> {
                exchOp.rhs = value
            }
            LLVMAtomicRMWBinOpAdd -> {
                val binaryOperator = newBinaryOperator("+", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpSub -> {
                val binaryOperator = newBinaryOperator("-", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpAnd -> {
                val binaryOperator = newBinaryOperator("&", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpNand -> {
                val binaryOperator = newBinaryOperator("|", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                val unaryOperator = newUnaryOperator("~", false, true, instrStr)
                unaryOperator.input = binaryOperator
                exchOp.rhs = unaryOperator
            }
            LLVMAtomicRMWBinOpOr -> {
                val binaryOperator = newBinaryOperator("|", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpXor -> {
                val binaryOperator = newBinaryOperator("^", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpMax -> {
                val condition = newBinaryOperator(">", instrStr)
                condition.lhs = ptrDeref
                condition.rhs = value
                val conditional = newConditionalExpression(condition, ptrDeref, value, ty)
                exchOp.rhs = conditional
            }
            LLVMAtomicRMWBinOpMin -> {
                val condition = newBinaryOperator("<", instrStr)
                condition.lhs = ptrDeref
                condition.rhs = value
                val conditional = newConditionalExpression(condition, ptrDeref, value, ty)
                exchOp.rhs = conditional
            }
            LLVMAtomicRMWBinOpUMax -> {
                val condition = newBinaryOperator(">", instrStr)
                val castExprLhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprLhs.castType = TypeParser.createFrom("u${ty.name}", true)
                castExprLhs.expression = ptrDeref
                condition.lhs = castExprLhs

                val castExprRhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprRhs.castType = TypeParser.createFrom("u${ty.name}", true)
                castExprRhs.expression = value
                condition.rhs = castExprRhs
                val conditional = newConditionalExpression(condition, ptrDeref, value, ty)
                exchOp.rhs = conditional
            }
            LLVMAtomicRMWBinOpUMin -> {
                val condition = newBinaryOperator("<", instrStr)
                val castExprLhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprLhs.castType = TypeParser.createFrom("u${ty.name}", true)
                castExprLhs.expression = ptrDeref
                condition.lhs = castExprLhs

                val castExprRhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprRhs.castType = TypeParser.createFrom("u${ty.name}", true)
                castExprRhs.expression = value
                condition.rhs = castExprRhs
                val conditional = newConditionalExpression(condition, ptrDeref, value, ty)
                exchOp.rhs = conditional
            }
            LLVMAtomicRMWBinOpFAdd -> {
                val binaryOperator = newBinaryOperator("+", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            LLVMAtomicRMWBinOpFSub -> {
                val binaryOperator = newBinaryOperator("-", instrStr)
                binaryOperator.lhs = ptrDeref
                binaryOperator.rhs = value
                exchOp.rhs = binaryOperator
            }
            else -> {
                throw Exception("LLVMAtomicRMWBinOp $operation not supported")
            }
        }

        return if (lhs != "") {
            // set lhs = *ptr, then perform the replacement
            val compoundStatement = newCompoundStatement(instrStr)
            compoundStatement.statements = listOf(declarationOrNot(ptrDeref, instr), exchOp)
            compoundStatement
        } else {
            // only perform the replacement
            exchOp
        }
    }

    private fun handleSelect(instr: LLVMValueRef): Statement {
        val cond = getOperandValueAtIndex(instr, 0)
        val value1 = getOperandValueAtIndex(instr, 1)
        val value2 = getOperandValueAtIndex(instr, 2)

        val conditionalExpr = newConditionalExpression(cond, value1, value2, value1.type)

        return declarationOrNot(conditionalExpr, instr)
    }

    /** Handles a [`br`](https://llvm.org/docs/LangRef.html#br-instruction) instruction. */
    private fun handleBrStatement(instr: LLVMValueRef): Statement {
        if (LLVMGetNumOperands(instr) == 3) {
            // if(op) then {label1} else {label2}
            val ifStatement = newConditionalBranchStatement(lang.getCodeFromRawNode(instr))
            val condition = getOperandValueAtIndex(instr, 0)
            val label1Name = LLVMGetValueName(LLVMGetOperand(instr, 1)).string

            // Set the default branch ("else")
            val elseLabelStatement =
                lang.labelMap.computeIfAbsent(label1Name) {
                    val label = newLabelStatement(label1Name)
                    label.name = label1Name
                    label
                }
            ifStatement.defaultTargetLabel = elseLabelStatement

            val label2Name =
                LLVMGetValueName(LLVMGetOperand(instr, 2)).string // The label of the if branch
            val thenLabelStatement =
                lang.labelMap.computeIfAbsent(label2Name) {
                    val label = newLabelStatement(label2Name)
                    label.name = label2Name
                    label
                }
            ifStatement.addConditionalTarget(condition, thenLabelStatement)

            return ifStatement
        } else if (LLVMGetNumOperands(instr) == 1) {
            // goto defaultLocation
            val gotoStatement = newGotoStatement(lang.getCodeFromRawNode(instr))
            val defaultLocation = LLVMGetOperand(instr, 0) // The BB of the target
            val labelName = LLVMGetValueName(defaultLocation).string

            val labelStatement =
                lang.labelMap.computeIfAbsent(labelName) {
                    val label = newLabelStatement(labelName)
                    label.name = labelName
                    label
                }
            gotoStatement.labelName = labelName
            gotoStatement.targetLabel = labelStatement

            return gotoStatement
        } else {
            throw Exception("Wrong number of operands in br statement")
        }
    }

    private fun handleSwitchStatement(instr: LLVMValueRef): Statement {
        val numOps = LLVMGetNumOperands(instr)
        if (numOps < 2) throw Exception("Switch statement without operand and default branch")

        val operand = getOperandValueAtIndex(instr, 0)

        val switchStatement = newConditionalBranchStatement(lang.getCodeFromRawNode(instr))

        val defaultLocation =
            LLVMGetValueName(LLVMGetOperand(instr, 1)).string // The label of the "default" branch

        val defaultLabelStatement =
            lang.labelMap.computeIfAbsent(defaultLocation) {
                val label = newLabelStatement(defaultLocation)
                label.name = defaultLocation
                label
            }

        switchStatement.defaultTargetLabel = defaultLabelStatement

        var idx = 2
        while (idx < numOps) {
            val binaryOperator = newBinaryOperator("==", lang.getCodeFromRawNode(instr))
            binaryOperator.lhs = operand
            binaryOperator.rhs = getOperandValueAtIndex(instr, idx)
            idx++
            val catchLabel = LLVMGetValueName(LLVMGetOperand(instr, idx)).string

            val catchLabelStatement =
                lang.labelMap.computeIfAbsent(catchLabel) {
                    val label = newLabelStatement(catchLabel)
                    label.name = catchLabel
                    label
                }

            switchStatement.addConditionalTarget(binaryOperator, catchLabelStatement)
            idx++
        }
        return switchStatement
    }

    private fun parseFunctionCall(instr: LLVMValueRef): Statement {
        val calledFunc = LLVMGetCalledValue(instr)
        val calledFuncName = LLVMGetValueName(calledFunc).string
        var param = LLVMGetFirstParam(calledFunc)
        var idx = 0

        val callExpr =
            newCallExpression(
                calledFuncName,
                calledFuncName,
                LLVMPrintValueToString(instr).string,
                false
            )

        while (param != null) {
            val operandName = getOperandValueAtIndex(instr, idx)
            callExpr.addArgument(operandName)
            param = LLVMGetNextParam(param)
            idx++
        }

        return declarationOrNot(callExpr, instr)
    }

    /**
     * Most instructions in LLVM have a variable assignment as part of their instruction. Since LLVM
     * IR is SSA, we need to declare a new variable in this case, which is named according to
     * [valueRef]. In case the variable assignment is optional, and we directly return the
     * [Expression] associated with the instruction.
     */
    private fun declarationOrNot(rhs: Expression, valueRef: LLVMValueRef): Statement {
        var lhs = valueRef.name
        var symbolName = valueRef.symbolName

        // it could be an unnamed variable
        if (lhs == "") {
            lhs = lang.guessSlotNumber(valueRef)
            symbolName = "%$lhs"
        }

        // if it is still empty, we probably do not have a left shide
        return if (lhs != "") {
            val decl = VariableDeclaration()
            decl.name = lhs
            decl.initializer = rhs

            // add the declaration to the current scope
            lang.scopeManager.addDeclaration(decl)

            // add it to our bindings cache
            lang.bindingsCache[symbolName] = decl

            val declStatement = DeclarationStatement()
            declStatement.singleDeclaration = decl
            declStatement
        } else {
            rhs
        }
    }

    /**
     * Handles a basic block and returns a [CompoundStatement] comprised of the statements of this
     * block.
     */
    private fun handleBasicBlock(bb: LLVMBasicBlockRef): CompoundStatement {
        val compound = newCompoundStatement("")

        var instr = LLVMGetFirstInstruction(bb)
        while (instr != null) {
            log.debug("Parsing {}", lang.getCodeFromRawNode(instr))

            val stmt = lang.statementHandler.handle(instr)

            compound.addStatement(stmt)

            instr = LLVMGetNextInstruction(instr)
        }

        return compound
    }

    private fun handleBinaryOperator(
        instr: LLVMValueRef,
        op: String,
        unsigned: Boolean,
        unordered: Boolean = false
    ): Statement {
        val op1 = getOperandValueAtIndex(instr, 0)
        val op2 = getOperandValueAtIndex(instr, 1)

        val binaryOperator: Expression
        var binOpUnordered: BinaryOperator? = null

        if (op == "uno") {
            // Unordered comparison operand => Replace with a call to isunordered(x, y)
            // Resulting statement: i1 lhs = isordered(op1, op2)
            binaryOperator =
                newCallExpression(
                    "isunordered",
                    "isunordered",
                    LLVMPrintValueToString(instr).string,
                    false
                )
            binaryOperator.addArgument(op1)
            binaryOperator.addArgument(op2)
        } else if (op == "ord") {
            // Ordered comparison operand => Replace with !isunordered(x, y)
            // Resulting statement: i1 lhs = !isordered(op1, op2)
            val unorderedCall =
                newCallExpression(
                    "isunordered",
                    "isunordered",
                    LLVMPrintValueToString(instr).string,
                    false
                )
            unorderedCall.addArgument(op1)
            unorderedCall.addArgument(op2)
            binaryOperator =
                newUnaryOperator("!", false, true, LLVMPrintValueToString(instr).string)
            binaryOperator.input = unorderedCall
        } else {
            // Resulting statement: lhs = op1 <op> op2.
            binaryOperator = newBinaryOperator(op, lang.getCodeFromRawNode(instr))

            if (unsigned) {
                val op1Type = "u${op1.type.typeName}"
                val castExprLhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprLhs.castType = TypeParser.createFrom(op1Type, true)
                castExprLhs.expression = op1
                binaryOperator.lhs = castExprLhs

                val op2Type = "u${op2.type.typeName}"
                val castExprRhs = newCastExpression(lang.getCodeFromRawNode(instr))
                castExprRhs.castType = TypeParser.createFrom(op2Type, true)
                castExprRhs.expression = op2
                binaryOperator.rhs = castExprRhs
            } else {
                binaryOperator.lhs = op1
                binaryOperator.rhs = op2
            }

            if (unordered) {
                // Special case for floating point comparisons which check if a value is "unordered
                // or <op>".
                // Statement is then lhs = isunordered(op1, op2) || (op1 <op> op2)
                binOpUnordered = newBinaryOperator("||", lang.getCodeFromRawNode(instr))
                binOpUnordered.rhs = binaryOperator
                val unorderedCall =
                    newCallExpression(
                        "isunordered",
                        "isunordered",
                        LLVMPrintValueToString(instr).string,
                        false
                    )
                unorderedCall.addArgument(op1)
                unorderedCall.addArgument(op2)
                binOpUnordered.lhs = unorderedCall
            }
        }

        val declOp = if (unordered) binOpUnordered else binaryOperator
        val decl = declarationOrNot(declOp!!, instr)

        (decl as? DeclarationStatement)?.let {
            // cache binding
            lang.bindingsCache[instr.symbolName] = decl.singleDeclaration as VariableDeclaration
        }

        return decl
    }

    private fun getOperandValueAtIndex(instr: LLVMValueRef, idx: Int): Expression {
        val operand = LLVMGetOperand(instr, idx)

        // there is also LLVMGetOperandUse, which might be of use to us

        return lang.expressionHandler.handle(operand) as Expression
    }
}
