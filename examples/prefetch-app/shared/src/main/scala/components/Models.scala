/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.runtime.json.PropsCodec

/** An item shown by the (deliberately slow) `Api.items` query. */
case class Item(id: Int, name: String, blurb: String) derives PropsCodec
