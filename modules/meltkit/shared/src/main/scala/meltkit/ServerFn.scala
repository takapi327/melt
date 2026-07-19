/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.json.{ PropsCodec, SimpleJson }

import meltkit.codec.{ BodyDecoder, BodyEncoder }

/** A typed, shared contract for a server function that a client can call as if it
  * were a local method.
  *
  * A `CommandFn` is created in shared code with [[ServerFn.command]], implemented
  * on the server with [[ServerMeltKitPlatform.serve]], and invoked from the
  * browser via the `apply` extension provided by the browser adapter. The three
  * faces — contract / implementation / client stub — all agree on the same
  * `In`/`Out` types because they share this single definition.
  *
  * The wire format is JSON produced by the [[melt.runtime.json.PropsCodec]] of
  * `In` and `Out`, so no external (circe) adapter is required.
  *
  * {{{
  * // shared
  * val like = ServerFn.command[PostId, Int]("posts.like")
  *
  * // jvm
  * app.serve(like) { (id, ctx) => postRepo.incLike(id) }
  *
  * // browser / .melt
  * <button onclick={_ => like(post.id)}>Like</button>
  * }}}
  *
  * @tparam In  the argument type (`Unit` when the function takes no input)
  * @tparam Out the result type
  */
/** The shared shape common to every server function — a typed endpoint plus the
  * `In`/`Out` JSON codecs — so that [[ServerMeltKitPlatform.serve]] can register
  * both a [[CommandFn]] and a [[QueryFn]] without caring which it is. */
sealed trait ServerFnContract[In, Out]:
  def name:                      String
  private[meltkit] def endpoint: Endpoint[PathSpec.Empty, In, Nothing, Out]
  private[meltkit] def inCodec:  PropsCodec[In]
  private[meltkit] def outCodec: PropsCodec[Out]

final class CommandFn[In, Out] private[meltkit] (
  val name:                      String,
  private[meltkit] val endpoint: Endpoint[PathSpec.Empty, In, Nothing, Out],
  private[meltkit] val inCodec:  PropsCodec[In],
  private[meltkit] val outCodec: PropsCodec[Out]
) extends ServerFnContract[In, Out]

/** A typed, shared contract for a read-only server function whose result is
  * exposed to the client as a reactive [[Query]].
  *
  * Created in shared code with [[ServerFn.query]], implemented on the server with
  * [[ServerMeltKitPlatform.serve]] (the same registration as a command), and
  * invoked from a component via the `apply` extension — which returns a [[Query]]
  * carrying a `Signal[melt.runtime.Async[Out]]` that renders `Loading` during SSR
  * and resolves reactively after hydration.
  *
  * {{{
  * // shared
  * val list = ServerFn.query[Unit, List[Post]]("posts.list")
  *
  * // jvm
  * app.serve(list) { (_, ctx) => postRepo.all }
  *
  * // component
  * val posts = list()
  * {posts.state.value match
  *   case Async.Loading    => <p>Loading…</p>
  *   case Async.Failed(e)  => <p class="error">{e.getMessage}</p>
  *   case Async.Done(list) => <ul>{list.map(p => <li>{p.title}</li>)}</ul>
  * }
  * }}}
  *
  * @tparam In  the argument type (`Unit` when the query takes no input)
  * @tparam Out the result type
  */
final class QueryFn[In, Out] private[meltkit] (
  val name:                      String,
  private[meltkit] val endpoint: Endpoint[PathSpec.Empty, In, Nothing, Out],
  private[meltkit] val inCodec:  PropsCodec[In],
  private[meltkit] val outCodec: PropsCodec[Out]
) extends ServerFnContract[In, Out]

/** Raised on the client when a server function call returns a non-2xx response.
  *
  * The rejected [[status]] and raw response [[body]] are carried so callers can
  * branch on failures. (Typed, per-function error channels are a later addition;
  * see the design doc §16.)
  */
final case class ServerFnException(status: Int, body: String)
  extends RuntimeException(s"server function failed with status $status")

object ServerFn:

  /** Reserved endpoint path prefix under which all generated server functions
    * live, e.g. `POST /_melt/fn/posts.like`. Kept in one place so routing,
    * adapters and the client stub agree. */
  private[meltkit] val prefix = "_melt/fn"

  /** Declares a mutation (non-form) server function.
    *
    * The returned [[CommandFn]] is a pure value: it carries the endpoint and the
    * `In`/`Out` codecs but contains no implementation, so it is safe to place in
    * shared code compiled for both the JVM server and the browser.
    */
  def command[In, Out](name: String)(using in: PropsCodec[In], out: PropsCodec[Out]): CommandFn[In, Out] =
    val ep = Endpoint[PathSpec.Empty, In, Nothing, Out](
      method          = "POST",
      spec            = PathSpec.fromString(s"$prefix/$name"),
      statusCode      = 200,
      bodyDecoder     = propsDecoder(in),
      responseEncoder = propsEncoder(out)
    )
    new CommandFn(name, ep, in, out)

  /** Declares a read-only (query) server function.
    *
    * Shares command's endpoint machinery — the same reserved path, JSON body,
    * and `In`/`Out` codecs — but the client side surfaces the result as a
    * reactive [[Query]] rather than a one-shot `Future`.
    */
  def query[In, Out](name: String)(using in: PropsCodec[In], out: PropsCodec[Out]): QueryFn[In, Out] =
    val ep = Endpoint[PathSpec.Empty, In, Nothing, Out](
      method          = "POST",
      spec            = PathSpec.fromString(s"$prefix/$name"),
      statusCode      = 200,
      bodyDecoder     = propsDecoder(in),
      responseEncoder = propsEncoder(out)
    )
    new QueryFn(name, ep, in, out)

  /** Adapts a symmetric [[PropsCodec]] into meltkit's request-body decoder,
    * turning a parse/type-mismatch failure into a client-safe [[BodyError]]. */
  private[meltkit] def propsDecoder[A](codec: PropsCodec[A]): BodyDecoder[A] =
    (body: String) =>
      try Right(codec.decode(SimpleJson.parse(body)))
      catch
        case e: IllegalArgumentException =>
          Left(BodyError.DecodeError("Invalid request body", Some(e.getMessage)))

  /** Adapts a symmetric [[PropsCodec]] into meltkit's response-body encoder. */
  private[meltkit] def propsEncoder[A](codec: PropsCodec[A]): BodyEncoder[A] =
    (value: A) => codec.encodeToString(value)
