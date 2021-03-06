package edu.cmu.cs.obsidian.typecheck

import edu.cmu.cs.obsidian.parser.Parser.Identifier
import edu.cmu.cs.obsidian.parser._

/* a type error: has a message (presented to the user).
 * The message generally depends on the parameters of the error: e.g. a subtyping error
 * is parametrized on the types T1 and T2 such that [T1 <: T2] was not satisfied */
abstract class Error {
    val msg: String
}

case class ErrorRecord (error: Error, pos: scala.util.parsing.input.Position, srcPath: String) extends Ordered[ErrorRecord] {
    def printMessage(): Unit = {
        println(s"$srcPath $pos: ${error.msg}")
    }

    def compare(that: ErrorRecord): Int = {
        if (this.srcPath < that.srcPath) -1
        else if (this.srcPath > that.srcPath) 1
        else if (this.pos < that.pos) -1
        else if (this.pos == that.pos) 0
        else 1
    }
}

case class NoMainContractError() extends Error {
    val msg: String = "No main contract found."
}

case class ShadowingError(fieldName: String, stateName: String, prevLine: Int) extends Error {
    val msg: String = s"Field '$fieldName' in '$stateName' also declared in contract at line $prevLine. Consider removing declaration in state."
}

case class ArgShadowingError(arg: String, transactionName: String, prevLine: Int) extends Error {
    val msg: String = s"The transaction parameter '$arg' in '$transactionName' is also declared in contract at line $prevLine. Consider changing the transaction parameter."
}

case class SharedFieldNameError(fieldName: String, stateName: String, prevLine: Int) extends Error {
    val msg: String = s"Field '$fieldName' previously declared in state '$stateName' at line $prevLine."
}
case class RepeatContractFields(fieldName: String, lineNum: Int, prevLine: Int) extends Error {
    val msg: String = s"Field '$fieldName' previously declared at line $prevLine. Consider removing line $lineNum."
}
case class CombineAvailableIns(fieldName: String, states: String, prevLine: Int) extends Error {
    val msg: String = s"Field '$fieldName' previously declared at line $prevLine. Did you mean '$fieldName available in $states'?"
}

case class SubtypingError(t1: ObsidianType, t2: ObsidianType, isThis : Boolean) extends Error {
    val msg: String =
        t1 match {
            case np: NonPrimitiveType =>
                if (np.permission == Inferred()) {
                    s"Found a local variable of unknown permission; ensure that a value was assigned to this variable."
                }
                else if (isThis) {

                    t2 match {
                        case np2: NonPrimitiveType =>
                            s"'this' is $np at the end of the transaction, but the transaction signature requires that it be $np2."
                        case _ => s"Found type '$t1', but expected something of type '$t2'."
                    }
                }
                else {
                    s"Found type '$t1', but expected something of type '$t2'."
                }
            case _ => s"Found type '$t1', but expected something of type '$t2'."
        }

}
case class VariableUndefinedError(x: String, context: String) extends Error {
    val msg: String = s"Variable '$x' is undefined in '$context'."

    override def equals(that: Any): Boolean = {
        that match {
            case that@VariableUndefinedError(y, _) => y == x
            case _ => false
        }
    }

    override def hashCode(): Int = x.hashCode()

}
case class DifferentTypeError(e1: Expression, t1: ObsidianType, e2: Expression, t2: ObsidianType) extends Error {
    val msg: String = s"Expression '$e1' has type '$t1', and expression '$e2' has type '$t2', " +
        s"but these expressions must have the same type."
}
case class FieldUndefinedError(fieldOf: NonPrimitiveType, fName: String) extends Error {
    val msg: String = fieldOf match {
        case ContractReferenceType(cName, _, _) => s"Field '$fName' is not defined in contract '$cName'"
        case StateType(cName, stateNames, _) => s"Field '$fName' is not defined in states '$stateNames' of contract '$cName'"
        case InterfaceContractType(name, typ) => s"Interfaces do not include fields."
        case GenericType(gVar, _) => val varName = gVar.varName
                                     s"Type variable '$varName' does not define field '$fName'."
    }
}
case class RecursiveFieldTypeError(cName: String, fName: String) extends Error {
    val msg: String = s"The type of field '$fName' in contract '$cName' recursively refers to itself"
}
case class RecursiveVariableTypeError(varName: String) extends Error {
    val msg: String = s"The type of variable '$varName' recursively refers to itself"
}
case class FieldNotConstError(cName: String, fName: String) extends Error {
    val msg: String = s"Field '$fName' must be labeled 'const' in contract '$cName'"
}
case class FieldConstMutationError(fName: String) extends Error {
    val msg: String = s"Field '$fName' cannot be mutated because it is labeled 'const'"
}
case class DereferenceError(typ: ObsidianType) extends Error {
    val msg: String = s"Type '$typ' cannot be dereferenced."
}
case class SwitchError(typ: ObsidianType) extends Error {
    val msg: String = s"Type '$typ' cannot be switched on."
}
case class MethodUndefinedError(receiver: NonPrimitiveType, name: String) extends Error {
    val msg: String = receiver match {
        case ContractReferenceType(cName, _, _) =>
            s"No transaction or function with name '$name' was found in contract '$cName'"
        case InterfaceContractType(cName, _) =>
            s"No transaction or function with name '$name' was found in interface '$cName'"
        case StateType(cName, sNames, _) =>
            s"No transaction or function with name '$name' was found in states '$sNames' of contract '$cName'"
        case GenericType(gVar, bound) =>
            s"No transaction or function with name '$name' was found in generic type $gVar implementing $bound."
    }
}
case class StateUndefinedError(cName: String, sName: String) extends Error {
    val msg: String = s"No state with name '$sName' was found in contract '$cName'"
}
case class ContractUndefinedError(cName: String) extends Error {
    val msg: String = s"No contract with name '$cName' is defined"
}
case class NonInvokeableError(t: ObsidianType) extends Error {
    val msg: String = s"Cannot invoke functions or transactions on type '$t'"
}
case class WrongArityError(expected: Int, have: Int, methName: String) extends Error {
    val msg: String =
        if (expected > have) {
            s"Too few arguments supplied to '$methName': expected '$expected', but found '$have'"
        } else {
            s"Too many arguments supplied to '$methName': expected '$expected', but found '$have'"
        }
}
case class MergeIncompatibleError(name: String, t1: ObsidianType, t2: ObsidianType) extends Error {
    val msg: String = s"Variable '$name' is incompatibly typed as both '$t1' and '$t2' after branch."
}
case class MustReturnError(methName: String) extends Error {
    val msg: String = s"'$methName' specifies a return type, but no return value is given."
}
case class CannotReturnError(methName: String) extends Error {
    val msg: String = s"'$methName' does not return anything, but a return value was given."
}
case class NotAValueError(methName: String) extends Error {
    val msg: String = s"'$methName' does not return anything, but is used here as a value."
}

case class TransitionUpdateError(mustSupply: Set[String]) extends Error {
    val fieldNames: String = mustSupply.mkString(", ")
    val msg: String = s"The following fields in the destination state may not be initialized: '$fieldNames'"
}
case class AssignmentError() extends Error {
    val msg: String = s"Assignment target must be a variable or a field."
}
//case class AlreadyKnowStateError(e: Expression, sName: String) extends Error {
//    val msg: String = s"'$e' is already known to be in state '$sName': a dynamic check is not needed"
//}

case class LeakReturnValueError(methName: String) extends Error {
    val msg: String = s"Invocation of '$methName' leaks ownership of return value."
}
case class NoEffectsError(s: Statement) extends Error {
    val msg: String = s"Statement '$s' has no side-effects."
}

case class UnusedOwnershipError(name: String) extends Error {
    val msg: String = s"Variable '$name' holds ownership, but is unused at the end of its scope."
}

case class UnusedExpressionOwnershipError(e: Expression) extends Error {
    val msg: String = s"Expression '$e' holds ownership, but is unused at the end of its scope."
}

case class UnusedExpressionArgumentOwnershipError(e: Expression) extends Error {
    val msg: String = s"Expression '$e' holds ownership, but is passed as an argument to a transaction that did not consume ownership."
}

case class LostOwnershipErrorDueToSharing(e: Expression) extends Error {
    val msg: String = s"Expression '$e' holds ownership, but is passed as an argument to a transaction that causes it to become Shared, losing ownership."
}

case class PotentiallyUnusedOwnershipError(name: String) extends Error {
    val msg: String = s"Variable '$name' holds ownership, but may be unused at the end of its scope."
}

case class OverwrittenOwnershipError(name: String) extends Error {
    val msg: String = s"Variable '$name' is an owning reference to an asset, so it cannot be overwritten."
}

case class StateInitializerOverwritesOwnership(name: String) extends Error {
    val msg: String = s"Field '$name' was initialized in a state initializer, but at the time of the transition, it is an owning reference to an asset, so it cannot be overwritten."
}

case class RedundantFieldInitializationError(name: String) extends Error {
    val msg: String = s"Field '$name' was initialized in both a state initializer as well as this transition. Remove one of the two initializations."
}

case class ConstructorNameError(contractName: String) extends Error {
    val msg: String = s"Invalid constructor name for contract '$contractName'"
}
case class CannotConvertPathError(badPart: String, expr: Expression, typ: NonPrimitiveType) extends Error {
    val msg: String = s"Cannot convert path in type '$typ': '$badPart' is equivalent to" +
        s"a non-variable expression '$expr'"
}
case class UnreachableCodeError() extends Error {
    val msg: String = s"Statement is unreachable"
}
case class NoStartStateError(contractName: String) extends Error {
    val msg: String = s"Constructor for '$contractName' does not transition to a named state"
}
case class NoConstructorError(contractName: String) extends Error {
    val msg: String = s"Contract '$contractName' must have a constructor since it contains states"
}

case class MultipleConstructorsError(contractName: String) extends Error {
    val msg: String = s"Main contract '$contractName' must only contain one constructor."
}

case class RepeatConstructorsError(contractName: String) extends Error {
    val msg: String = s"Contract '$contractName' has multiple constructors that take the same arguments."
}

case class NoParentError(cName: String) extends Error {
    val msg: String = s"Contract $cName has no parent contract"
}

case class AssetContractConstructorError(contractName: String) extends Error {
    val msg: String = s"Constructors in asset contract $contractName must return owned references."
}

case class OwnershipSubtypingError(t1: ObsidianType, t2: ObsidianType) extends Error {
    val msg: String = s"Can't transfer ownership to type '$t2' from unowned type '$t1'."
}

case class NonAssetOwningAssetError(contractName: String, f: Field) extends Error {
    val msg: String = s"Non-asset contract '$contractName' cannot own asset field '${f.name}' of type '${f.typ}'."
}

case class DisownUnowningExpressionError(e: Expression) extends Error {
    val msg: String = s"Can't disown expression that is not already owned: '$e'."
}

case class InvalidStateFieldInitialization(stateName: String, fieldName: String) extends Error {
    val msg: String = s"Can't assign to field $fieldName without transitioning to its state, $stateName."
}

case class NonStaticAccessError(method: String, name:String) extends Error {
    val msg: String = s"Cannot invoke a non-static transaction '$method' via a static reference '$name'"
}

case class StaticAssertOnPrimitiveError(e: Expression) extends Error {
    val msg: String = s"Cannot check the ownership or state of primitive expression '$e'."
}

case class StaticAssertFailed(e: Expression, statesOrPermissions: TypeState, actualType: ObsidianType) extends Error {
    val stateStr = statesOrPermissions.toString

    val msg: String = s"Expression '$e' failed assertion $stateStr. Actual type: $actualType."
}

case class StaticAssertInvalidState(contractName: String, stateOrPermission: String) extends Error {
    val msg: String = s"Cannot assert for invalid state '$stateOrPermission' in contract $contractName"
}

case class ArgumentSpecificationError(arg: String, transactionName: String, t1: ObsidianType, t2: ObsidianType) extends Error {
    val msg: String = s"The argument '$arg' in '$transactionName' was specified to end as type '$t1', " +
        s"but actually ends as type '$t2'."
}

case class InvalidNonThisFieldAssignment() extends Error {
    val msg: String = "Cannot assign to fields of variables other than 'this'."
}

case class InvalidNonThisFieldAccess() extends Error {
    val msg: String = "Cannot read fields of variables other than 'this'. Instead, use an accessor transaction."
}


case class InvalidInconsistentFieldType(fieldName: String, actualType: ObsidianType, expectedType: ObsidianType) extends Error {
    val msg: String = s"When transactions exit, all fields must reference objects consistent with their declared types. " +
        s" Field '$fieldName' is of type $actualType but was declared as $expectedType."
}

case class TransitionNotAllowedError() extends Error {
    val msg: String = "Cannot change state because 'this' was specified to not allow state changes."
}

case class ReceiverTypeIncompatibleError(transactionName: String, actualType: ObsidianType, expectedType: ObsidianType) extends Error {
    val msg: String = s"Cannot invoke $transactionName on a receiver of type $actualType; a receiver of type $expectedType is required."
}

case class InconsistentContractTypeError(declaredContractName: ContractType, actualContractName: ContractType) extends Error {
    val msg: String = s"Cannot assign a value of contract $actualContractName to a variable that requires a value of contract $declaredContractName."
}

case class InconsistentTypeAssignmentError(declaredType: ObsidianType, actualType: ObsidianType) extends Error {
    val msg: String = s"Cannot assign a value of type $actualType to a variable of type $declaredType."
}

case class ArgumentSubtypingError(tName: String, arg: String, t1: ObsidianType, t2: ObsidianType) extends Error {
    val msg: String = s"Found type '$t1' as an argument to '$tName', but the argument '$arg' is expected to be something of type '$t2'."
}

case class FieldTypesDeclaredOnPublicTransactionError(tName: String) extends Error {
    val msg: String = s"Transaction $tName is public, so it cannot have initial or final field types specified."
}

case class InvalidFinalFieldTypeDeclarationError(fieldName: String) extends Error {
    val msg: String = s"Field $fieldName does not exist, so it cannot have a different declared final type."
}

case class FieldSubtypingError(fieldName: String, actualType: ObsidianType, expectedType: ObsidianType) extends Error {
    val msg: String = s"Field $fieldName needs to be of type $expectedType but is actually of type $actualType."
}


case class FieldMissingPermissionError(fieldName: String) extends Error {
    val msg: String = s"Field $fieldName needs to have a permission, such as Owned, Unowned, or Shared."
}

case class ReturnTypeMissingPermissionError(typeName: String) extends Error {
    val msg: String = s"Return type $typeName needs to have a permission, such as Owned, Unowned, or Shared."
}

case class InvalidLocalVariablePermissionDeclarationError() extends Error {
    val msg: String = s"Local variable declarations cannot include states or permissions. They must be inferred from the type of the assigned value."
}

case class ImportError(msg: String) extends Error {
}

case class DuplicateContractError(contractName: String) extends Error {
    val msg: String = s"Duplicate contract $contractName."
}

case class UninitializedFieldError(fieldName: String) extends Error {
    val msg: String = s"Field '$fieldName' must be initialized in this constructor, but the constructor might not initialize it."
}

case class StateCheckOnPrimitiveError() extends Error {
    val msg: String = s"Can't check the state of a primitive-type expression."
}

case class StateCheckRedundant() extends Error {
    val msg: String = s"State check will always pass/fail. Remove redundant code."
}

case class InvalidValAssignmentError() extends Error {
    val msg: String = s"Can't reassign to variables that are formal parameters or which are used in a dynamic state check."
}

case class InvalidOwnershipLossInDynamicCheck(variableName: String) extends Error {
    val msg: String = s"Dynamic state tests must not give away ownership of the variable being tested (${variableName})."
}

case class AmbiguousConstructorExample(example: String, arg1: VariableDeclWithSpec, arg2: VariableDeclWithSpec) {
    val msg: String =
        s"Cannot distinguish the type of the argument ${arg1.varName} of type ${arg1.typIn} from ${arg2.varName} of type ${arg2.typIn}.\n" +
        s"For example, a value of type $example could be either ${arg1.typIn} or ${arg2.typIn}."
}

case class AmbiguousConstructorError(contractName: String,
                                     examples: Seq[AmbiguousConstructorExample]) extends Error {
    val msg: String = s"Constructors are ambiguous in contract $contractName: " +
        examples.map(_.msg).mkString("\n") + "\n" +
        s"Either delete one of the conflicting constructors or change the argument types so that they are distinguishable."
}

case class ConstructorAnnotationMissingError(contractName: String) extends Error {
    val msg: String = s"Missing a permission on the result type of a constructor of $contractName " +
                      s"(e.g., $contractName@Owned, $contractName@State, where State is a valid state for $contractName)."
}

case class InterfaceInstantiationError(contractName: String) extends Error {
    val msg: String = s"Cannot instantiate interface $contractName."
}

case class InterfaceNotFoundError(contractName: String, interfaceName: String) extends Error {
    val msg: String = s"Cannot find the interface $interfaceName that $contractName implements."
}

case class InterfaceConstructorError() extends Error {
    val msg: String = "Interfaces are not allowed to contain constructors."
}

case class MethodImplArgsError(transName: String,
                               actualList: Seq[VariableDeclWithSpec],
                               expectedList: Seq[VariableDeclWithSpec]) extends Error {
    val msg: String = s"Transaction $transName implements a transaction, but the argument lists are incompatible:\n " +
                      s"Actual: $actualList\n" +
                      s"Expected: $expectedList"
}

case class MethodImplReturnTypeError(transName: String,
                                     retType: Option[ObsidianType],
                                     interfaceRetType: Option[ObsidianType]) extends Error {
    val msg: String =
        if (retType.isDefined) {
            s"Transaction $transName returns ${retType.get}, but the transaction it implements has no return type."
        } else {
            s"Transaction $transName has no return type, but the transaction it implements returns ${interfaceRetType.get}."
        }
}

case class MissingStateImplError(contractName: String, interfaceName: String, stateName: String) extends Error {
    val msg: String =
        s"Missing state $stateName from interface $interfaceName in contract $contractName."
}

case class MissingTransactionImplError(contractName: String, interfaceName: String,
                                       transactionName: String) extends Error {
    val msg: String =
        s"Missing transaction $transactionName from interface $interfaceName in contract $contractName."
}

case class GenericParameterListError(paramsLength: Int, actualLength: Int) extends Error {
    val msg: String =
        s"Expected $paramsLength generic parameters but got $actualLength parameters"
}

case class GenericParameterError(param: GenericType, actualArg: ObsidianType) extends Error {
    val msg: String =
        s"Argument $actualArg is not compatible with generic parameter $param"
}

case class GenericParameterAssetError(paramName: String, argTypeName: String) extends Error {
    val msg: String =
        s"Argument type $argTypeName is an asset, but the type parameter $paramName is not allowed to be an asset. Add the 'asset' modifier to $paramName to allow the parameter to be an asset."
}

case class GenericParameterPermissionMissingError(param: GenericType, permVariable: String, actualArg: ObsidianType) extends Error {
    val msg: String = s"Missing specification for a value for the permission variable $permVariable in $param in type parameter $actualArg."
}

case class PermissionCheckRedundant(actualPerm: Permission, testPermission: Permission,
                                    isSubperm: Boolean) extends Error {
    private val isSubpermStr: String =
        if (isSubperm) {
            "is a subpermission. The then-branch will always be taken"
        } else {
            "is not a subpermission. The else-branch will always be taken"
        }

    override val msg: String =
        s"Redundant permission check. Testing for permission $testPermission, but actual permission is $actualPerm, " +
            s"which $isSubpermStr."
}

case class AssetStateImplError(implState: State, interfaceState: State) extends Error {
    override val msg: String =
        s"State ${implState.name} is an asset state but it implements a state that is not an asset state."
}

case class BadFFIInterfaceBoundError(name: String) extends Error {
    override val msg: String =
        s"Cannot override '$name' because it is implemented in Java, not Obsidian."
}

case class GenericParamShadowError(param1Binder: String, param1: GenericType,
                                   param2Binder: String, param2: GenericType) extends Error {
    override val msg: String = s"$param1 in $param1Binder shadows $param2 of $param2Binder."
}

case class FieldTypesIncompatibleAcrossBranches(fieldName: String, typ: ObsidianType, branch: String) extends Error {
    override val msg: String = s"Field $fieldName must have compatible types in both branches, but it was given incompatible type $typ in the $branch branch."
}

case class StateInitializerUninitialized(stateName: String, fieldName: String) extends Error {
    override val msg: String = s"$stateName::$fieldName has not been initialized. Perhaps you are " +
        s"trying to use a field initializer for general-purpose field access instead of only for " +
        s"preparing for a transition."
}

case class DuplicateArgName(argName: String) extends Error {
    override val msg: String = s"All argument names must be different from each other, but '$argName' occurs more than once."
}

case class FieldsNotAllowedInInterfaces(fieldName: String, interfaceName: String) extends Error {
    override val msg: String = s"Field '$fieldName' cannot be defined on interface '$interfaceName' because interfaces cannot define fields."
}

case class VariableShadowingDisallowed(variableName: String) extends Error {
    override val msg: String = s"Variable '$variableName' shadows or redeclares a variable already in scope, which is not allowed." +
                                " Choose a different name for one of the variables."
}