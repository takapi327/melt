/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.preprocessor

/** Generic preprocessor abstraction.
  *
  * A preprocessor transforms source text from one form to another before the
  * main compilation pipeline processes it.
  *
  * @tparam In  the input type (e.g. [[StyleInput]] for stylesheet preprocessing)
  * @tparam Out the output type (e.g. `String` for compiled CSS)
  */
trait Preprocessor[In, Out]:

  /** Transform `input` into the target representation.
    *
    * @return [[Right]] on success, [[Left]] with an error message on failure
    */
  def process(input: In): Either[String, Out]
