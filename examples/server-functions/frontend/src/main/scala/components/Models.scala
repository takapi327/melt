/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.runtime.json.PropsCodec

import meltkit.codec.FormDataDecoder

/** A post shown in the reactive list and mutated by the `like`/`remove` commands. */
case class Post(id: Int, title: String, likes: Int) derives PropsCodec

/** The "write a post" form model. `errors` is a per-field issue map
  * (`Map[field, messages]`) — enabled by `PropsCodec[Map[String, V]]` — so the
  * server can surface a validation message next to each input. */
case class NewPost(title: String, body: String, errors: Map[String, List[String]] = Map.empty)
  derives FormDataDecoder,
          PropsCodec
