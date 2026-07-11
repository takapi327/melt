/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.Action
import melt.runtime.forms.FormHandle

/** SSR (JVM) placeholder for the `use:enhance={form}` action.
  *
  * `use:` actions are client-only — the SSR emitter ignores them — so this is a
  * no-op. It exists so that a `.melt` component with `import meltkit.enhance`
  * compiles on the JVM (SSR) side as well as the JS (hydration) side. The real
  * behaviour is in the Scala.js implementation.
  */
val enhance: Action[FormHandle] = Action((_, _) => () => ())
