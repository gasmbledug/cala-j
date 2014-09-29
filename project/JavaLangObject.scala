/*
 * Hard-coded IR for java.lang.Object.
 */

import scala.scalajs.ir
import ir._
import ir.Definitions._
import ir.Infos._
import ir.Trees._
import ir.Types._
import ir.Position.NoPosition

/** Hard-coded IR for java.lang.Object.
 *  We cannot so much as begin to fake a compilation of java.lang.Object,
 *  because Object is hijacked so much by scalac itself that it does not like
 *  at all to try to compile that class. So we have to bypass entirely the
 *  compiler to define java.lang.Object.
 */
object JavaLangObject {

  /** Optimizer hints with `@inline`
   *  Unfortunately we do not have access to private details of
   *  [[OptimizerHints]], so we cannot do this cleanly. But it is fine
   *  somehow because we're part of the same project implementation.
   */
  private def inlineOptimizerHints = new OptimizerHints(2)

  val InfoAndTree = (Info, Definition)

  private def Info = ClassInfo(
    name = "java.lang.Object",
    encodedName = "O",
    ancestorCount = 0,
    kind = ClassKind.Class,
    superClass = "",
    ancestors = List("O"),
    methods = List(
      MethodInfo("__init__"),
      MethodInfo("init___"),
      MethodInfo("hashCode__I"),
      MethodInfo("equals__O__Z",
        optimizerHints = inlineOptimizerHints
      ),
      MethodInfo("clone__O",
        calledMethods = Map(
          "jl_CloneNotSupportedException" -> List("init___")
        ),
        instantiatedClasses = List("jl_CloneNotSupportedException"),
        accessedClassData = List("jl_Cloneable"),
        optimizerHints = inlineOptimizerHints
      ),
      MethodInfo("notify__V"),
      MethodInfo("notifyAll__V"),
      MethodInfo("toString__T",
        calledMethods = Map(
          "O" -> List("hashCode__I"),
          "jl_Class" -> List("getName__T"),
          "jl_Integer$" -> List("toHexString__I__T")
        ),
        accessedModules = List("jl_Integer")
      ),
      MethodInfo("finalize__V"),
      MethodInfo("clone__",
        calledMethods = Map(
          "O" -> List("clone__O")
        )
      ),
      MethodInfo("notify__",
        calledMethods = Map(
          "O" -> List("notify__V")
        )
      ),
      MethodInfo("notifyAll__",
        calledMethods = Map(
          "O" -> List("notifyAll__V")
        )
      ),
      MethodInfo("finalize__",
        calledMethods = Map(
          "O" -> List("finalize__V")
        )
      )
    )
  )

  private def Definition = {
    implicit val DummyPos = NoPosition

    // ClassType(Object) is normally invalid, but not in this class def
    val ThisType = ClassType(ObjectClass)

    ClassDef(
      Ident("O", Some("java.lang.Object")),
      ClassKind.Class,
      None,
      Nil,
      List(
        /* def this() = () */
        MethodDef(
          Ident("init___", Some("<init>")),
          Nil,
          AnyType,
          This()(ThisType)),

        /* def hashCode(): Int = 42 */
        MethodDef(
          Ident("hashCode__I", Some("hashCode__I")),
          Nil,
          IntType,
          {
            CallHelper("systemIdentityHashCode", This()(ThisType))(IntType)
          }),

        /* def equals(that: Object): Boolean = this eq that */
        MethodDef(
          Ident("equals__O__Z", Some("equals__O__Z")),
          List(ParamDef(Ident("that", Some("that")), AnyType, mutable = false)),
          BooleanType,
          {
            BinaryOp(BinaryOp.===,
              This()(ThisType),
              VarRef(Ident("that", Some("that")), mutable = false)(AnyType))
          }),

        /* protected def clone(): Object =
         *   if (this.isInstanceOf[Cloneable]) <clone>(this)
         *   else throw new CloneNotSupportedException()
         */
        MethodDef(
          Ident("clone__O", Some("clone__O")),
          Nil,
          AnyType,
          {
            If(IsInstanceOf(This()(ThisType), ClassType("jl_Cloneable")), {
              CallHelper("cloneObject", This()(ThisType))(AnyType)
            }, {
              Throw(New(ClassType("jl_CloneNotSupportedException"),
                Ident("init___", Some("<init>")), Nil))
            })(AnyType)
          }),

        /* def toString(): String =
         *   getClass().getName() + "@" + Integer.toHexString(hashCode())
         */
        MethodDef(
          Ident("toString__T", Some("toString__T")),
          Nil,
          ClassType(StringClass),
          {
            BinaryOp(BinaryOp.String_+, BinaryOp(BinaryOp.String_+,
              Apply(
                CallHelper("objectGetClass", This()(ThisType))(ClassType(ClassClass)),
                Ident("getName__T"), Nil)(ClassType(StringClass)),
              // +
              StringLiteral("@")),
              // +
              Apply(
                LoadModule(ClassType("jl_Integer$")),
                Ident("toHexString__I__T"),
                List(Apply(This()(ThisType), Ident("hashCode__I"), Nil)(IntType)))(
                ClassType(StringClass)))
          }),

          /* Since wait() is not supported in any way, a correct implementation
           * of notify() and notifyAll() is to do nothing.
           */

          /* def notify(): Unit = () */
          MethodDef(
            Ident("notify__V", Some("notify__V")),
            Nil,
            NoType,
            Skip()),

          /* def notifyAll(): Unit = () */
          MethodDef(
            Ident("notifyAll__V", Some("notifyAll__V")),
            Nil,
            NoType,
            Skip()),

          /* def finalize(): Unit = () */
          MethodDef(
            Ident("finalize__V", Some("finalize__V")),
            Nil,
            NoType,
            Skip()),

          /* Reflective proxies
           * Note that we do not need to proxy the following methods, since
           * they are defined on Any in the Scala hierarchy and therefore a
           * reflective call is never emitted:
           * - equals
           * - getClass
           * - hashCode
           * - toString
           */

          MethodDef(Ident("clone__"), Nil, AnyType,
            Apply(This()(ThisType), Ident("clone__O"), Nil)(AnyType)),

          MethodDef(Ident("notify__"), Nil, AnyType, Block(
            Apply(This()(ThisType), Ident("notify__V"), Nil)(NoType),
            Undefined())),

          MethodDef(Ident("notifyAll__"), Nil, AnyType, Block(
            Apply(This()(ThisType), Ident("notifyAll__V"), Nil)(NoType),
            Undefined())),

          MethodDef(Ident("finalize__"), Nil, AnyType, Block(
            Apply(This()(ThisType), Ident("finalize__V"), Nil)(NoType),
            Undefined())),

          // Exports

          /* JSExport for toString(). */
          MethodDef(
            StringLiteral("toString"),
            Nil,
            AnyType,
            {
              Apply(This()(ThisType),
                  Ident("toString__T", Some("toString__T")),
                  Nil)(ClassType(StringClass))
            })
      ))
  }

}
