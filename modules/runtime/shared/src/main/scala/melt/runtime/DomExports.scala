/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

// Re-export DOM types so that `import melt.runtime.*` makes them available.
export melt.runtime.dom.{
  Element,
  Event,
  EventTarget,
  FocusEvent,
  InputElement,
  InputEvent,
  KeyboardEvent,
  MouseEvent,
  Node,
  SubmitEvent
}
