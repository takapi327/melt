/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.forms.Form

/** Connects form actions to reactive queries: when the form submits
  * successfully, the declared queries are refreshed so the UI reflects the
  * mutation — the SvelteKit "invalidate on success" / TanStack `invalidateQueries`
  * pattern.
  *
  * {{{
  * val posts = Api.list.seeded(props.posts)
  * val form  = Form(props.form.getOrElse(NewPost("", ""))).invalidates(posts)
  * // on a successful <form use:enhance={form}> submit, `posts` refreshes
  * }}}
  *
  * Registered via [[melt.runtime.forms.Form.onSuccess]], so it runs after the
  * built-in success handling and only on the client (SSR never runs an enhance
  * result). On the JVM `Query.refresh` is a no-op, so this is inert during SSR.
  */
extension [A](form: Form[A])
  def invalidates(queries: Query[?]*): Form[A] =
    form.onSuccess(() => queries.foreach(_.refresh()))
    form
