package components

import meltkit.RouteRegistry

/** In-scope registry for the hand-written `route"..."` interpolator used in `App.melt`'s
  * expression-form link. The compiler-emitted links use `checkedRouteFor[routes.Routes.type]`
  * and need no given; this one covers the given-based API that stays available for hand-written
  * call sites. Kept in a separate scope from the route `val`s so `param`'s implicit search
  * never evaluates it.
  */
given RouteRegistry[routes.Routes.type] = RouteRegistry()
