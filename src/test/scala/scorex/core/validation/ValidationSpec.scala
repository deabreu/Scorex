package scorex.core.validation

import org.scalatest.{FlatSpec, Matchers}
import scorex.core.bytesToId
import scorex.core.consensus.ModifierSemanticValidity
import scorex.core.utils.ScorexEncoding
import scorex.core.validation.ValidationResult._

import scala.util.{Failure, Try}

class ValidationSpec extends FlatSpec with Matchers with ScorexEncoding {

  val errMsg1 = "Error message 1"
  val errMsg3 = "Error message 3"

  val map: Map[Short, (String => Invalid, Boolean)] = Map(
    1.toShort -> (s => fatal(errMsg1), true),
    3.toShort -> (_ => fatal(errMsg3), true),
    4.toShort -> (_ => fatal("Error message 4"), true),
    5.toShort -> (_ => error("Error message 5"), true),
    10.toShort -> (_ => error("Deactivated check"), false)
  )

  val ffSettings: ValidationSettings = new MapValidationSettings(true, map)
  val aeSettings: ValidationSettings = new MapValidationSettings(false, map)

  /** Start validation in Fail-Fast mode */
  def failFast: ValidationState[Unit] = ModifierValidator.apply(ffSettings)

  /** Start validation accumulating all the errors */
  def accumulateErrors: ValidationState[Unit] = ModifierValidator.apply(aeSettings)

  /** report recoverable modifier error that could be fixed by later retries */
  def error(errorMessage: String): Invalid = ModifierValidator.error(errorMessage)

  /** report non-recoverable modifier error that could be fixed by retries and requires modifier change */
  def fatal(errorMessage: String): Invalid = ModifierValidator.fatal(errorMessage)

  /** unsuccessful validation with a given error */
  def invalid(error: ModifierError): Invalid = ModifierValidator.invalid(error)

  /** successful validation */
  def success: Valid[Unit] = ModifierValidator.success

  val trueCondition: String => Boolean = _ => true

  "ModifierValidation" should "be able to succeed when failing fast" in {
    val result = failFast
      .validate(1, condition = true)
      .result
    result.isValid shouldBe true
    result shouldBe a[Valid[_]]
  }

  it should "skip deactivated checks" in {
    val result = failFast
      .validate(10, condition = false)
      .result
    result.isValid shouldBe true
    result shouldBe a[Valid[_]]
  }

  it should "fail for missed checks" in {
    val result = failFast
      .validate(2, condition = false)
      .result
    result shouldBe an[Invalid]
    result.errors should have size 1
  }

  it should "be able to succeed when accumulating errors" in {
    val result = accumulateErrors
      .validate(1, condition = true)
      .result

    result.isValid shouldBe true
    result shouldBe a[Valid[_]]
  }

  it should "support fail fast approach" in {
    val result = failFast
      .validate(1, condition = false)
      .validate(3, condition = false)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 1
  }

  it should "support error accumulation" in {
    val result = accumulateErrors
      .validate(1, condition = false)
      .validate(3, condition = false)
      .validate(4, condition = true)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 2
  }

  it should "be lazy" in {
    var i = 0
    val result = failFast
      .validate(1, condition = false)
      .validate(3, s"${i = 1}" == s"${()}")
      .result
    result.isValid shouldBe false
    result shouldBe an[Invalid]
    i shouldBe 0
  }

  it should "support recoverable errors" in {
    val result = accumulateErrors
      .validate(5, condition = false)
      .result

    result shouldBe an[Invalid]
    result.errors should have size 1
    all(result.errors) shouldBe a[RecoverableModifierError]
    result.asInstanceOf[Invalid].isFatal shouldBe false
  }

  it should "support fatal errors" in {
    val result = accumulateErrors
      .validate(5, condition = false)
      .validate(1, condition = false)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 2
    exactly(1, result.errors) shouldBe a[MalformedModifierError]
    result.asInstanceOf[Invalid].isFatal shouldBe true
  }

  it should "support accumulating nesting" in {
    val result = accumulateErrors
      .validate(1, condition = false)
      .validate {
        failFast
          .validate(3, condition = true)
          .validate(4, condition = false)
          .validate(5, condition = false)
          .result
      }
      .result

    result.isValid shouldBe false
    result.errors should have size 2
  }

  it should "support fail fast nesting" in {
    val result = failFast
      .validate(1, condition = true)
      .validate {
        accumulateErrors
          .validate(2, condition = true)
          .validate(3, condition = false)
          .validate(4, condition = false)
          .result
      }
      .result

    result.isValid shouldBe false
    result.errors should have size 2
  }

  it should "fail fast while nesting" in {
    val result = failFast
      .validate(3, condition = false)
      .validate {
        accumulateErrors
          .validate(4, condition = false)
          .validate(5, condition = false)
          .result
      }
      .result

    result.isValid shouldBe false
    result.errors.map(_.message) shouldBe Seq(errMsg3)
  }

  it should "correctly check byte array equality" in {
    val len = 32
    val byte1 = 1.toByte
    val byte2 = 2.toByte
    val id = bytesToId(Array.fill(len)(byte1))
    val result = accumulateErrors
      .validateEqualIds(1, id, bytesToId(Array.fill(len)(byte2)))
      .validateEqualIds(4, id, bytesToId(Array.fill(len)(byte1)))
      .validateEqualIds(3, id, bytesToId(Array.fill(len + 1)(byte1)))
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 2
    result.errors.map(_.message) should contain only(errMsg1, errMsg3)
  }

  it should "correctly check equality" in {
    val result = accumulateErrors
      .validateEquals(1, "123", "12" + "3")
      .validateEquals(3, "123", "122")
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 1
    result.errors.map(_.message) should contain only errMsg3
  }

  it should "correctly check semantic validity" in {
    val result = accumulateErrors
      .validateSemantics(1, ModifierSemanticValidity.Valid)
      .validateSemantics(2, ModifierSemanticValidity.Absent)
      .validateSemantics(3, ModifierSemanticValidity.Unknown)
      .validateSemantics(4, ModifierSemanticValidity.Invalid)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 1
    result.errors.map(_.message) should contain only ModifierSemanticValidity.Invalid.toString
  }

  it should "support `not` condition" in {
    val result = accumulateErrors
      .validateNot(1, condition = false)
      .validateNot(3, condition = true)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors should have size 1
    result.errors.map(_.message) should contain only errMsg3
  }

  it should "carry payload" in {
    val data = 1L
    val result = accumulateErrors
      .payload(data)
      .validate(1, condition = true)
      .result

    result.isValid shouldBe true
    result shouldBe Valid(data)
    result.payload shouldBe Some(data)
  }

  it should "replace payload" in {
    val initialData = "Hi there"
    val data = 1L
    val result = accumulateErrors
      .payload(initialData)
      .validate(1, condition = true)
      .result(data)

    result.isValid shouldBe true
    result shouldBe Valid(data)
    result.payload shouldBe Some(data)
  }

  it should "fill payload from try" in {
    val data = "Hi there"
    val result = accumulateErrors
      .payload("Random string")
      .validateTry[String](1, Try(data), trueCondition)
      .result

    result.isValid shouldBe true
    result shouldBe Valid(data)
    result.payload shouldBe Some(data)
  }

  it should "return error when filling payload from failure" in {
    val errMsg = "Should not take payload from failure"
    val result = accumulateErrors
      .payload("Random string")
      .validateTry(1, Failure(new Error("Failed")), trueCondition)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors.map(_.message) should contain(errMsg)
  }

  it should "aggregate payload from try" in {
    val data1 = 100L
    val data2 = 50L
    val expected = data1 / data2
    val result = accumulateErrors
      .payload[Long](data1)
      .validateTry[Long](1, Try(data2), _ => true)
      .result

    result.isValid shouldBe true
    result shouldBe Valid(expected)
    result.payload shouldBe Some(expected)
  }

  it should "return error when aggregating payload from failure" in {
    val errMsg = "Should not take payload from failure"
    val result = accumulateErrors
      .payload(1)
      .validateTry[Int](1, Failure(new Error("Failed")): Try[Int], _ => true)
      .result

    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.errors.map(_.message) should contain(errMsg)
  }

  it should "switch failure payload type" in {
    val errMsg = "Failure 1"
    val stringFailure: ValidationState[String] = failFast
      .payload("Hi there")
      .pass(fatal(errMsg))

    val unitFailure: ValidationState[Unit] = stringFailure
      .pass(success)
      .pass(fatal(errMsg + "23"))

    val result = unitFailure.result
    result.isValid shouldBe false
    result shouldBe an[Invalid]
    result.payload shouldBe empty
    result.errors should have size 1
    result.errors.map(_.message) should contain only errMsg
  }

  it should "validate optional for some" in {
    val expression = "123"
    val cnt = expression.length
    val result = accumulateErrors
      .validateOrSkip(Some(expression)) { (validation, expr) =>
        validation.validate(expr.length == cnt)(fatal("Should never happen"))
      }
      .result

    result.isValid shouldBe true
    result shouldBe a[Valid[_]]
  }

  it should "skip optional validation for none" in {
    val result = accumulateErrors
      .validateOrSkip(None) { (validation, _) =>
        validation.validate(condition = false)(fatal("Should never happen"))
      }
      .result

    result.isValid shouldBe true
    result shouldBe a[Valid[_]]
  }

}
