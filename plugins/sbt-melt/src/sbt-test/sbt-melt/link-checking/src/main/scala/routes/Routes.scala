package routes

import meltkit.*

/** The single source of truth for the app's routes. Each `TypedRoute` carries its full
  * path (static segments + typed params) in the type, so the route macro can validate
  * links against it at compile time.
  */
object Routes:
  // No type annotation: the inferred `TypedRoute[(id: Int), ("users", PathParam["id", Int])]`
  // is what the route macro reflects on. Widening to `TypedRoute[?, ?]` would erase Segs.
  val list = TypedRoute.root / "users"
  val user = TypedRoute.root / "users" / param[Int]("id")
