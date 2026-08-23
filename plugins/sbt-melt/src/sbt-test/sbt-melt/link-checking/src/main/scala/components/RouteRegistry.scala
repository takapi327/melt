package components

import meltkit.RouteRegistry

/** In-scope registry for the generated components in this package. The `route"..."` calls that
  * sbt-melt emits (because `meltLinkChecking := true`) resolve this given and validate every
  * URL-attribute link against `routes.Routes`. Kept in a separate scope from the route `val`s
  * so `param`'s implicit search never evaluates it.
  */
given RouteRegistry[routes.Routes.type] = RouteRegistry()
