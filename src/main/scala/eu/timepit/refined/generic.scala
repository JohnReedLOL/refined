package eu.timepit.refined

import eu.timepit.refined.InferenceRuleAlias.==>
import eu.timepit.refined.generic._
import eu.timepit.refined.internal.WeakWitness

object generic extends GenericPredicates with GenericInferenceRules {

  /** Predicate that checks if a value is equal to `U`. */
  trait Equal[U]
}

trait GenericPredicates {

  implicit def equalPredicate[T, U <: T](implicit wu: WeakWitness.Aux[U]): Predicate[Equal[U], T] =
    Predicate.instance(_ == wu.value, t => s"($t == ${wu.value})")
}

trait GenericInferenceRules {

  implicit def equalPredicateInference[T, U <: T, P](implicit p: Predicate[P, T], wu: WeakWitness.Aux[U]): Equal[U] ==> P =
    InferenceRule(p.isValid(wu.value))
}
