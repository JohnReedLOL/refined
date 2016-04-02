package eu.timepit.refined

import eu.timepit.refined.api.{ Inference, Result, Validate }
import eu.timepit.refined.api.Inference.==>
import eu.timepit.refined.boolean.Not
import eu.timepit.refined.collection._
import eu.timepit.refined.generic.Equal
import eu.timepit.refined.internal.Resources
import eu.timepit.refined.numeric.{ GreaterEqual, LessEqual }
import shapeless.Witness

/**
 * Module for collection predicates.
 *
 * @group modules
 */
object collection extends CollectionValidate with CollectionInference {

  /**
   * Predicate that counts the number of elements in a `TraversableOnce`
   * which satisfy the predicate `PA` and passes the result to the numeric
   * predicate `PC`.
   */
  case class Count[PA, PC](pa: PA, pc: PC)

  /** Predicate that checks if a `TraversableOnce` is empty. */
  case class Empty()

  /**
   * Predicate that checks if the predicate `P` holds for all elements of a
   * `TraversableOnce`.
   */
  case class Forall[P](p: P)

  /**
   * Predicate that checks if the predicate `P` holds for the first element
   * of a `Traversable`.
   */
  case class Head[P](p: P)

  /**
   * Predicate that checks if the predicate `P` holds for the element at
   * index `N` of a sequence.
   */
  case class Index[N, P](n: N, p: P)

  /**
   * Predicate that checks if the predicate `P` holds for the last element
   * of a `Traversable`.
   */
  case class Last[P](p: P)

  /**
   * Predicate that checks if the size of a `TraversableOnce` satisfies the
   * predicate `P`.
   */
  case class Size[P](p: P)

  /**
   * Predicate that checks if a `TraversableOnce` contains a value
   * equal to `U`.
   */
  type Contains[U] = Exists[Equal[U]]

  /**
   * Predicate that checks if the predicate `P` holds for some elements of a
   * `TraversableOnce`.
   */
  type Exists[P] = Not[Forall[Not[P]]]

  /**
   * Predicate that checks if the size of a `TraversableOnce` is greater than
   * or equal to `N`.
   */
  type MinSize[N] = Size[GreaterEqual[N]]

  /**
   * Predicate that checks if the size of a `TraversableOnce` is less than
   * or equal to `N`.
   */
  type MaxSize[N] = Size[LessEqual[N]]

  /** Predicate that checks if a `TraversableOnce` is not empty. */
  type NonEmpty = Not[Empty]
}

private[refined] trait CollectionValidate {

  implicit def countValidate[A, PA, RA, PC, RC, T](
    implicit
    va: Validate.Aux[A, PA, RA],
    vc: Validate.Aux[Int, PC, RC],
    ev: T => TraversableOnce[A]
  ): Validate.Aux[T, Count[PA, PC], Count[List[va.Res], vc.Res]] =
    new Validate[T, Count[PA, PC]] {
      override type R = Count[List[va.Res], vc.Res]

      override def validate(t: T): Res = {
        val ra = t.toList.map(va.validate)
        val rc = vc.validate(ra.count(_.isPassed))
        rc.as(Count(ra, rc))
      }

      override def showExpr(t: T): String =
        vc.showExpr(count(t))

      override def showResult(t: T, r: Res): String = {
        val c = count(t)
        val expr = t.toList.map(va.showExpr).mkString("count(", ", ", ")")
        Resources.predicateTakingResultDetail(s"$expr = $c", r, vc.showResult(c, r.detail.pc))
      }

      private def count(t: T): Int =
        t.count(va.isValid)
    }

  implicit def emptyValidate[T](implicit ev: T => TraversableOnce[_]): Validate.Plain[T, Empty] =
    Validate.fromPredicate(_.isEmpty, t => s"isEmpty($t)", Empty())

  implicit def forallValidate[A, P, R, T[a] <: TraversableOnce[a]](
    implicit
    v: Validate.Aux[A, P, R]
  ): Validate.Aux[T[A], Forall[P], Forall[List[v.Res]]] =
    new Validate[T[A], Forall[P]] {
      override type R = Forall[List[v.Res]]

      override def validate(t: T[A]): Res = {
        val rt = t.toList.map(v.validate)
        Result.fromBoolean(rt.forall(_.isPassed), Forall(rt))
      }

      override def showExpr(t: T[A]): String =
        t.toList.map(v.showExpr).mkString("(", " && ", ")")
    }

  implicit def forallValidateView[A, P, R, T](
    implicit
    v: Validate.Aux[A, P, R],
    ev: T => TraversableOnce[A]
  ): Validate.Aux[T, Forall[P], Forall[List[v.Res]]] =
    forallValidate[A, P, R, TraversableOnce].contramap(ev)

  implicit def headValidate[A, P, R, T[a] <: Traversable[a]](
    implicit
    v: Validate.Aux[A, P, R]
  ): Validate.Aux[T[A], Head[P], Head[Option[v.Res]]] =
    new Validate[T[A], Head[P]] {
      override type R = Head[Option[v.Res]]

      override def validate(t: T[A]): Res = {
        val ra = t.headOption.map(v.validate)
        Result.fromBoolean(ra.fold(false)(_.isPassed), Head(ra))
      }

      override def showExpr(t: T[A]): String =
        optElemShowExpr(t.headOption, v.showExpr)

      override def showResult(t: T[A], r: Res): String =
        optElemShowResult(t.headOption, r.detail.p, (a: A) => s"head($t) = $a", v.showResult)
    }

  implicit def headValidateView[A, P, R, T](
    implicit
    v: Validate.Aux[A, P, R],
    ev: T => Traversable[A]
  ): Validate.Aux[T, Head[P], Head[Option[v.Res]]] =
    headValidate[A, P, R, Traversable].contramap(ev)

  implicit def indexValidate[A, P, R, N <: Int, T](
    implicit
    v: Validate.Aux[A, P, R],
    ev: T => PartialFunction[Int, A],
    wn: Witness.Aux[N]
  ): Validate.Aux[T, Index[N, P], Index[N, Option[v.Res]]] =
    new Validate[T, Index[N, P]] {
      override type R = Index[N, Option[v.Res]]

      override def validate(t: T): Res = {
        val ra = t.lift(wn.value).map(v.validate)
        Result.fromBoolean(ra.fold(false)(_.isPassed), Index(wn.value, ra))
      }

      override def showExpr(t: T): String =
        optElemShowExpr(t.lift(wn.value), v.showExpr)

      override def showResult(t: T, r: Res): String =
        optElemShowResult(t.lift(wn.value), r.detail.p, (a: A) => s"index($t, ${wn.value}) = $a", v.showResult)
    }

  implicit def lastValidate[A, P, R, T[a] <: Traversable[a]](
    implicit
    v: Validate.Aux[A, P, R]
  ): Validate.Aux[T[A], Last[P], Last[Option[v.Res]]] =
    new Validate[T[A], Last[P]] {
      override type R = Last[Option[v.Res]]

      override def validate(t: T[A]): Res = {
        val ra = t.lastOption.map(v.validate)
        Result.fromBoolean(ra.fold(false)(_.isPassed), Last(ra))
      }

      override def showExpr(t: T[A]): String =
        optElemShowExpr(t.lastOption, v.showExpr)

      override def showResult(t: T[A], r: Res): String =
        optElemShowResult(t.lastOption, r.detail.p, (a: A) => s"last($t) = $a", v.showResult)
    }

  implicit def lastValidateView[A, P, R, T](
    implicit
    v: Validate.Aux[A, P, R],
    ev: T => Traversable[A]
  ): Validate.Aux[T, Last[P], Last[Option[v.Res]]] =
    lastValidate[A, P, R, Traversable].contramap(ev)

  implicit def sizeValidate[T, P, RP](
    implicit
    v: Validate.Aux[Int, P, RP],
    ev: T => TraversableOnce[_]
  ): Validate.Aux[T, Size[P], Size[v.Res]] =
    new Validate[T, Size[P]] {
      override type R = Size[v.Res]

      override def validate(t: T): Res = {
        val r = v.validate(t.size)
        r.as(Size(r))
      }

      override def showExpr(t: T): String =
        v.showExpr(t.size)

      override def showResult(t: T, r: Res): String = {
        val size = t.size
        val nested = v.showResult(size, r.detail.p)
        Resources.predicateTakingResultDetail(s"size($t) = $size", r, nested)
      }
    }

  private def optElemShowExpr[A](elem: Option[A], f: A => String): String =
    elem.fold(Resources.showExprEmptyCollection)(f)

  private def optElemShowResult[A, R](elem: Option[A], res: Option[Result[R]], f: A => String, g: (A, Result[R]) => String): String =
    (elem, res) match {
      case (Some(a), Some(r)) =>
        Resources.predicateTakingResultDetail(f(a), r, g(a, r))
      case _ => Resources.showResultEmptyCollection
    }
}

private[refined] trait CollectionInference {

  implicit def existsInference[A, B](implicit p1: A ==> B): Exists[A] ==> Exists[B] =
    p1.adapt("existsInference(%s)")

  implicit def existsNonEmptyInference[P]: Exists[P] ==> NonEmpty =
    Inference.alwaysValid("existsNonEmptyInference")

  implicit def headInference[A, B](implicit p1: A ==> B): Head[A] ==> Head[B] =
    p1.adapt("headInference(%s)")

  implicit def headExistsInference[P]: Head[P] ==> Exists[P] =
    Inference.alwaysValid("headExistsInference")

  implicit def indexInference[N, A, B](implicit p1: A ==> B): Index[N, A] ==> Index[N, B] =
    p1.adapt("indexInference(%s)")

  implicit def indexExistsInference[N, P]: Index[N, P] ==> Exists[P] =
    Inference.alwaysValid("indexExistsInference")

  implicit def lastInference[A, B](implicit p1: A ==> B): Last[A] ==> Last[B] =
    p1.adapt("lastInference(%s)")

  implicit def lastExistsInference[P]: Last[P] ==> Exists[P] =
    Inference.alwaysValid("lastExistsInference")

  implicit def sizeInference[A, B](implicit p1: A ==> B): Size[A] ==> Size[B] =
    p1.adapt("sizeInference(%s)")
}
