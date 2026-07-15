/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import cats.effect.*
import com.comcast.ip4s.*
import components.*
import generated.AssetManifest
import meltkit.*
import meltkit.adapter.http4s.CirceBodyDecoder.given
import meltkit.adapter.http4s.CirceBodyEncoder.given
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import org.http4s.ember.server.EmberServerBuilder

/** Type-safe Server Functions example — the whole data layer over one contract.
  *
  * Demonstrates, end to end:
  *   - '''query + seed''': `Api.list` is implemented with `app.serve`, and the
  *     `/` page renders it as a prop so the client hydrates with data (no flash).
  *   - '''command''': `Api.like` / `Api.remove` mutate the store.
  *   - '''single-flight''': the client's `like`/`remove` refresh `Api.list` in the
  *     same round-trip (see `PostsPage.melt`), re-running the query below.
  *   - '''optimistic''': the like count bumps immediately, reconciling with the
  *     value this handler returns.
  *   - '''field issues''': the `/new` action returns `NewPost` with a per-field
  *     `errors` map on validation failure.
  *
  * {{{ sbt "server-functions-server/run" }}}
  */
object Server extends IOApp.Simple:

  private def buildApp(store: Ref[IO, List[Post]], nextId: Ref[IO, Int]): MeltKit[IO] =
    val app = MeltKit[IO]()
    app.use(ServerHook.csrf[IO]())

    // ── Server functions: implemented once, callable type-safely from the client ─
    app.serve(Api.list) { (_, _) => store.get }

    app.serve(Api.like) { (id, _) =>
      store.modify { posts =>
        val updated = posts.map(p => if p.id == id then p.copy(likes = p.likes + 1) else p)
        (updated, updated.find(_.id == id).getOrElse(Post(id, "", 0)))
      }
    }

    app.serve(Api.remove) { (id, _) => store.update(_.filterNot(_.id == id)) }

    // ── Pages ────────────────────────────────────────────────────────────────
    // Loader: read the store and seed the reactive query as a prop.
    app.get("") { ctx =>
      store.get.map(posts => ctx.render(PostsPage(PostsPage.Props(posts = posts))))
    }

    // Field-issues form: validation failures return a per-field `errors` map.
    app.page("new")(
      render = (_, form: Option[NewPost]) => NewPostPage(NewPostPage.Props(form = form)),
      action = ctx =>
        ctx.body.form[NewPost].flatMap {
          case Right(f) =>
            val issues = Map.newBuilder[String, List[String]]
            if f.title.trim.isEmpty then issues += "title"    -> List("Title is required")
            if f.body.trim.length < 10 then issues += "body"  -> List("Body must be at least 10 characters")
            val errs = issues.result()
            if errs.nonEmpty then IO.pure(fail(422, f.copy(errors = errs)))
            else
              nextId.getAndUpdate(_ + 1).flatMap { id =>
                store.update(_ :+ Post(id, f.title.trim, 0)).as(ActionResult.Redirect("/"))
              }
          case Left(err) =>
            IO.pure(fail(400, NewPost("", "", Map("_form" -> err.messages))))
        }
    )

    app

  def run: IO[Unit] =
    for
      store  <- Ref.of[IO, List[Post]](List(Post(1, "Hello Melt", 3), Post(2, "Server Functions", 7)))
      nextId <- Ref.of[IO, Int](3)
      httpApp <- Http4sAdapter
                   .ssrRoutes(
                     buildApp(store, nextId),
                     fs2.io.file.Path(AssetManifest.clientDistDir),
                     AssetManifest.manifest
                   )
                   .map(_.orNotFound)
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"3000")
             .withHttpApp(httpApp)
             .build
             .use(_ => IO.println("server-functions → http://localhost:3000") *> IO.never)
    yield ()
