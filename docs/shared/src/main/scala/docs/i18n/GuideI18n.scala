/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs.i18n

case class GuideNav(prev: String, next: String)

// ── Introduction ──────────────────────────────────────────────────────────────

case class GuideIntroduction(
  lead1:          String,
  sfcFramework:   String,
  lead2:          String,
  keyIdeasH2:     String,
  keyIdeasIntro:  String,
  idea1Bold:      String,
  idea1Text:      String,
  idea2Bold:      String,
  idea2Text:      String,
  idea3Bold:      String,
  idea3Text:      String,
  firstLookH2:    String,
  firstLookIntro: String,
  in15Lines:      String,
  li15_1:         String,
  li15_2:         String,
  li15_3:         String,
  li15_4:         String,
  compilesH2:     String,
  compilesIntro:  String,
  step1:          String,
  step2:          String,
  step3:          String,
  step4:          String,
  outputText:     String,
  calloutTitle:   String,
  calloutText:    String,
  calloutLink:    String,
  notH2:          String,
  not1Pre:        String,
  not1Kit:        String,
  not1Post:       String,
  not2:           String,
  not3:           String
)

// ── Installation ──────────────────────────────────────────────────────────────

case class GuideInstallation(
  lead:          String,
  prereqH2:      String,
  prereq1:       String,
  prereq2:       String,
  prereq3:       String,
  prereq4:       String,
  step1H2:       String,
  step1Intro:    String,
  step2H2:       String,
  step2Intro:    String,
  step3H2:       String,
  step3Intro:    String,
  step3Layout:   String,
  step4H2:       String,
  step4Intro:    String,
  snapshotTitle: String,
  snapshotText:  String,
  nextStepTitle: Option[String],
  nextStepText:  Option[String],
  nextStepLink:  Option[String]
)

// ── Quick Start ───────────────────────────────────────────────────────────────

case class GuideQuickStart(
  lead:         String,
  step1H3:      String,
  step1Text:    String,
  step1Link:    String,
  step2H3:      String,
  step2Intro:   String,
  step2Outro:   Option[String],
  step3H3:      String,
  step3Intro:   String,
  step4H3:      String,
  step5H3:      String,
  step5Intro:   String,
  step5Outro:   String,
  noSetupTitle: String,
  noSetupText:  String,
  noSetupLink:  String
)

// ── Components ────────────────────────────────────────────────────────────────

case class GuideComponents(
  lead:             String,
  fileStructH2:     String,
  propsH2:          String,
  propsIntro:       String,
  propsAccess:      String,
  usageH2:          String,
  usageIntro:       String,
  childrenH2:       String,
  childrenIntro:    String,
  defaultsH2:       Option[String],
  defaultsText:     Option[String],
  scopedStylesH2:   String,
  scopedStylesText: String,
  noteTitle:        String,
  noteText:         String,
  noteLinkText:     String
)

// ── Template Syntax ───────────────────────────────────────────────────────────

case class GuideTemplateSyntax(
  lead:            String,
  exprH2:          String,
  exprIntro:       String,
  exprSignalText:  String,
  attrH2:          String,
  attrIntro:       String,
  twoWayH2:        String,
  twoWayIntro:     String,
  bindColH:        String,
  bindTargetH:     String,
  bindDescH:       String,
  bindValueDesc:   String,
  bindCheckedDesc: String,
  bindThisDesc:    String,
  classH2:         String,
  classIntro:      String,
  classMulti:      String,
  styleH2:         String,
  styleIntro:      String,
  eventsH2:        String,
  eventsIntro:     String,
  spreadH2:        String,
  spreadIntro:     String,
  refH2:           String,
  refIntro:        String,
  commentTitle:    Option[String],
  commentText:     Option[String]
)

// ── Reactivity ────────────────────────────────────────────────────────────────

case class GuideReactivity(
  lead:          String,
  stateH2:       String,
  stateIntro:    String,
  stateReadText: String,
  mutateH2:      String,
  mutateIntro:   String,
  signalH2:      String,
  signalIntro:   String,
  signalUsage:   String,
  domH2:         String,
  domIntro:      String,
  calloutTitle:  String,
  calloutText:   String
)

// ── Computed ──────────────────────────────────────────────────────────────────

case class GuideComputed(
  lead:             String,
  mapH2:            String,
  mapIntro:         String,
  flatMapH2:        String,
  flatMapIntro:     String,
  memoH2:           String,
  memoIntro:        String,
  memoCalloutTitle: String,
  memoCalloutText:  String,
  combineH2:        String,
  combineIntro:     String
)

// ── Effects ───────────────────────────────────────────────────────────────────

case class GuideEffects(
  lead:         String,
  basicH2:      String,
  basicIntro:   String,
  multiH2:      String,
  multiIntro:   String,
  cleanupH2:    String,
  cleanupIntro: String
)

// ── Events ────────────────────────────────────────────────────────────────────

case class GuideEvents(
  lead:            String,
  basicH2:         String,
  basicOutro:      String,
  eventObjH2:      String,
  eventTableIntro: String,
  handlerH:        String,
  typeH:           String,
  useH:            String,
  row1Use:         String,
  row2Use:         String,
  row3Use:         String,
  row4Use:         String,
  row5Use:         String,
  row6Use:         String,
  bindValueH2:     String,
  bindValueIntro:  String,
  windowH2:        String,
  windowIntro:     String,
  windowOutro:     Option[String],
  customTitle:     Option[String],
  customText:      Option[String]
)

// ── Lifecycle ─────────────────────────────────────────────────────────────────

case class GuideLifecycle(
  lead:             String,
  onMountH2:        String,
  onMountIntro:     String,
  ssrTitle:         String,
  ssrText:          String,
  cleanupH2:        String,
  cleanupIntro:     String,
  effectCleanH2:    String,
  effectCleanIntro: String,
  destroyH2:        Option[String],
  destroyText:      Option[String],
  warnTitle:        Option[String],
  warnText:         Option[String]
)

// ── Control Flow ──────────────────────────────────────────────────────────────

case class GuideControlFlow(
  lead:          String,
  condH2:        String,
  condIntro:     String,
  whyMapTitle:   String,
  whyMapText:    String,
  listH2:        String,
  listIntro:     String,
  keyedH2:       Option[String],
  keyedText:     Option[String],
  keyBlockH2:    String,
  keyBlockIntro: String,
  keyBlockOutro: String,
  emptyH2:       String,
  emptyIntro:    String
)

// ── Special Elements ──────────────────────────────────────────────────────────

case class GuideSpecialElements(
  lead:          String,
  headH2:        String,
  headIntro:     String,
  windowH2:      String,
  windowIntro:   String,
  windowOutro:   String,
  boundaryH2:    String,
  boundaryIntro: String,
  elementH2:     String,
  elementIntro:  String,
  documentH2:    Option[String],
  documentIntro: Option[String],
  snippetsH2:    String,
  snippetsIntro: String,
  tableElemH:    Option[String],
  tableMountH:   Option[String],
  tableUseH:     Option[String]
)

// ── Transitions ───────────────────────────────────────────────────────────────

case class GuideTransitions(
  lead:          String,
  tweenH2:       String,
  tweenIntro:    String,
  tweenOutro:    String,
  springH2:      String,
  springIntro:   String,
  optionH:       String,
  defaultH:      String,
  descH:         String,
  stiffnessDesc: String,
  dampingDesc:   String,
  precisionDesc: String,
  cssH2:         String,
  cssIntro:      String,
  inOutH2:       Option[String],
  inOutIntro:    Option[String],
  perfTitle:     Option[String],
  perfText:      Option[String]
)

// ── Trusted HTML ──────────────────────────────────────────────────────────────

case class GuideTrustedHtml(
  lead:            String,
  whyH2:           String,
  whyIntro:        String,
  whyOutro:        String,
  unsafeH2:        String,
  unsafeIntro:     String,
  warnTitle:       String,
  warnText:        String,
  sanitizeH2:      String,
  sanitizeIntro:   String,
  trustedUrlH2:    String,
  trustedUrlIntro: String,
  trustedUrlOutro: String,
  secTableH2:      Option[String]
)

// ── CSS ───────────────────────────────────────────────────────────────────────

case class GuideCss(
  lead:          String,
  scopedH2:      String,
  scopedIntro:   String,
  scopedGenText: String,
  globalH2:      Option[String],
  globalIntro:   Option[String],
  dynamicH2:     String,
  dynamicIntro:  String,
  customH2:      String,
  customIntro:   String,
  scssH2:        String,
  scssIntro:     String,
  dartTitle:     String,
  dartText:      String,
  nestingH2:     Option[String],
  nestingIntro:  Option[String]
)

// ── Testing ───────────────────────────────────────────────────────────────────

case class GuideTesting(
  lead:           String,
  setupH2:        String,
  setupIntro:     String,
  writingH2:      String,
  apiH2:          String,
  methodH:        String,
  descH:          String,
  mountDesc:      String,
  textDesc:       String,
  clickDesc:      String,
  inputDesc:      String,
  existsDesc:     String,
  findAllDesc:    String,
  getByTextDesc:  String,
  getByRoleDesc:  String,
  waitForDesc:    String,
  reactiveH2:     Option[String],
  reactiveIntro:  Option[String],
  eventH2:        Option[String],
  jvmTitle:       Option[String],
  jvmText:        Option[String],
  formH2:         String,
  formIntro:      String,
  formServerH3:   String,
  formServerDesc: String,
  formClientH3:   String,
  formClientDesc: String,
  formCodecH3:    String,
  formCodecDesc:  String
)

// ── Routing ───────────────────────────────────────────────────────────────────

case class GuideRouting(
  lead:            String,
  setupH2:         String,
  setupIntro:      String,
  routesH2:        String,
  pathParamsH2:    String,
  pathParamsIntro: String,
  pathParamsOutro: Option[String],
  ctxTableH2:      Option[String],
  ctxMethodH:      Option[String],
  ctxDescH:        Option[String],
  ctxRenderDesc:   Option[String],
  ctxHtmlDesc:     Option[String],
  ctxParamsDesc:   Option[String],
  ctxQueryDesc:    Option[String],
  ctxLocalsDesc:   Option[String],
  pageOptsH2:      String,
  pageOptsIntro:   String,
  infoTitle:       Option[String],
  infoText:        Option[String]
)

// ── SSR ───────────────────────────────────────────────────────────────────────

case class GuideSsr(
  lead:         String,
  howH2:        String,
  step1:        String,
  step2:        String,
  step3:        String,
  step4:        String,
  enableH2:     String,
  enableIntro:  String,
  routeH2:      String,
  propsH2:      String,
  propsIntro:   String,
  viteH2:       Option[String],
  viteIntro:    Option[String],
  partialTitle: String,
  partialText:  String,
  spaVsSsrH2:   Option[String]
)

// ── SSG ───────────────────────────────────────────────────────────────────────

case class GuideSsg(
  lead:        String,
  enableH2:    String,
  enableIntro: String,
  runH2:       String,
  runIntro:    String,
  runCmd:      String,
  outputH2:    String,
  deployH2:    Option[String],
  deployIntro: Option[String],
  deployLi1:   Option[String],
  deployLi2:   Option[String],
  deployLi3:   Option[String],
  deployLi4:   Option[String],
  dynTitle:    Option[String],
  dynText:     Option[String]
)

// ── Adapters ──────────────────────────────────────────────────────────────────

case class GuideAdapters(
  lead:         String,
  http4sH2:     String,
  http4sIntro:  String,
  nodeH2:       String,
  nodeIntro:    String,
  browserH2:    String,
  browserIntro: String,
  cmpH2:        String,
  adapterH:     String,
  platformH:    String,
  viaVite:      String,
  choiceH2:     Option[String],
  choiceLi1Pre: Option[String],
  choiceLi1Kit: Option[String],
  choiceLi2Pre: Option[String],
  choiceLi2Kit: Option[String],
  choiceLi3Pre: Option[String],
  choiceLi3Kit: Option[String],
  choiceLi4Pre: Option[String],
  multiTitle:   Option[String],
  multiText:    Option[String]
)

// ── Form Actions ────────────────────────────────────────────────────────────────

case class GuideFormActions(
  lead:             String,
  howH2:            String,
  howIntro:         String,
  step1:            String,
  step2:            String,
  step3:            String,
  serverH2:         String,
  serverIntro:      String,
  singleH3:         String,
  singleIntro:      String,
  namedH3:          String,
  namedIntro:       String,
  resultH2:         String,
  resultIntro:      String,
  resultSuccess:    String,
  resultFailure:    String,
  resultRedirect:   String,
  clientH2:         String,
  clientIntro:      String,
  nameOfTitle:      String,
  nameOfText:       String,
  controlsH2:       String,
  controlsIntro:    String,
  customH2:         String,
  customIntro:      String,
  csrfH2:           String,
  csrfIntro:        String,
  reactivityTitle:  String,
  reactivityText:   String,
  progressiveTitle: String,
  progressiveText:  String
)

// ── Server Functions ────────────────────────────────────────────────────────

case class GuideServerFunctions(
  lead:              String,
  whatH2:            String,
  whatProblem:       String,
  whatModel:         String,
  whatServerOnly:    String,
  whatKinds:         String,
  contractH2:        String,
  contractIntro:     String,
  queryH2:           String,
  queryIntro:        String,
  commandH2:         String,
  commandIntro:      String,
  singleFlightH2:    String,
  singleFlightIntro: String,
  optimisticH2:      String,
  optimisticIntro:   String,
  issuesH2:          String,
  issuesIntro:       String,
  invalidateH2:      String,
  invalidateIntro:   String,
  serverOnlyTitle:   String,
  serverOnlyText:    String,
  handlerTitle:      String,
  handlerText:       String
)

// ── Top-level Guide container ─────────────────────────────────────────────────

case class GuideI18n(
  nav:             GuideNav,
  introduction:    GuideIntroduction,
  installation:    GuideInstallation,
  quickStart:      GuideQuickStart,
  components:      GuideComponents,
  templateSyntax:  GuideTemplateSyntax,
  reactivity:      GuideReactivity,
  computed:        GuideComputed,
  effects:         GuideEffects,
  events:          GuideEvents,
  lifecycle:       GuideLifecycle,
  controlFlow:     GuideControlFlow,
  specialElements: GuideSpecialElements,
  transitions:     GuideTransitions,
  trustedHtml:     GuideTrustedHtml,
  css:             GuideCss,
  testing:         GuideTesting,
  routing:         GuideRouting,
  ssr:             GuideSsr,
  ssg:             GuideSsg,
  adapters:        GuideAdapters,
  formActions:     GuideFormActions,
  serverFunctions: GuideServerFunctions
)

object GuideI18n:

  val en: GuideI18n = GuideI18n(
    nav = GuideNav(prev = "← Previous", next = "Next →"),

    introduction = GuideIntroduction(
      lead1        = "Melt is a ",
      sfcFramework = "Single File Component (SFC) framework for Scala.js",
      lead2        =
        " inspired by Svelte. You write your logic, markup, and styles in a single .melt file, and the compiler turns it into efficient, direct DOM code — no virtual DOM, no runtime framework overhead.",
      keyIdeasH2    = "Key ideas",
      keyIdeasIntro = "Melt is built around three simple ideas:",
      idea1Bold     = "The compiler does the work.",
      idea1Text = " Reactivity isn't a library you import — it's woven into the generated code by the Melt compiler.",
      idea2Bold = "Scala types check your templates.",
      idea2Text = " Every expression inside {} braces is real Scala, checked by scalac at compile time.",
      idea3Bold = "One source, three targets.",
      idea3Text =
        " The same .melt file compiles to SPA (Scala.js DOM code), SSR (JVM HTML string), or SSG (static HTML files).",
      firstLookH2    = "A first look",
      firstLookIntro = "Here is a complete interactive counter in Melt:",
      in15Lines      = "In 15 lines you have:",
      li15_1         = "A mutable reactive cell (State(0))",
      li15_2         = "A derived value that updates automatically (count.map(_ * 2))",
      li15_3         = "Event handlers that mutate state (count += 1)",
      li15_4         = "Scoped CSS that only applies to this component",
      compilesH2     = "How it compiles",
      compilesIntro  = "The Melt compiler reads your .melt file through this pipeline:",
      step1          = "Parse the <script>, template, and <style> sections",
      step2          = "Run semantic checks (type hints, a11y warnings, security checks)",
      step3          = "Lower the template AST to an internal IR",
      step4          = "Generate Scala code via the SPA or SSR emitter",
      outputText     =
        "The output is a plain Scala object you compile with scalac/Scala.js as normal. There is no Melt runtime in the browser — just the tiny reactive primitives you actually use.",
      calloutTitle = "Try it now",
      calloutText  = "Open the ",
      calloutLink  = "Playground",
      notH2        = "What Melt is not",
      not1Pre      = "It is not a full-stack meta-framework by itself (that role belongs to ",
      not1Kit      = "MeltKit",
      not1Post     = ", covered in the Server section).",
      not2         = "It does not ship a virtual DOM — updates are fine-grained and targeted.",
      not3         = "It does not require React, Vue, or any JS framework."
    ),

    installation = GuideInstallation(
      lead =
        "Melt integrates into your existing sbt project via an sbt plugin. This page walks you through the required setup from scratch.",
      prereqH2   = "Prerequisites",
      prereq1    = "sbt 1.9+",
      prereq2    = "Scala 3.3.7+ (for the compiler module; Scala 3.8+ for MeltKit)",
      prereq3    = "Node.js 18+ (for Vite dev server and bundling)",
      prereq4    = "JDK 17+",
      step1H2    = "1 · Add the sbt plugin",
      step1Intro = "Create or edit project/plugins.sbt:",
      step2H2    = "2 · Configure build.sbt",
      step2Intro = "Enable the plugin on your Scala.js module:",
      step3H2    = "3 · Project structure",
      step3Intro =
        "The plugin expects .melt files under src/main/scala (or any configured source directory). Generated Scala sources are placed in target/scala-3.x.x/src_managed/main/melt/ and compiled automatically.",
      step3Layout = "Typical layout",
      step4H2     = "4 · Vite setup (optional)",
      step4Intro  =
        "Melt works with any bundler, but Vite is the recommended choice for development. Add a vite.config.mjs at your project root:",
      snapshotTitle = "SNAPSHOT releases",
      snapshotText  =
        "Melt is currently in active development. Add the Sonatype snapshots resolver to project/repositories or build.sbt if dependencies are not found.",
      nextStepTitle = None,
      nextStepText  = None,
      nextStepLink  = None
    ),

    quickStart = GuideQuickStart(
      lead       = "This guide builds a reactive counter from zero to running in under 5 minutes.",
      step1H3    = "Create the project",
      step1Text  = "Start from the Melt counter example or create a minimal sbt project with the plugin enabled (see ",
      step1Link  = "Installation",
      step2H3    = "Write your first component",
      step2Intro = "Create src/main/scala/Counter.melt:",
      step2Outro = None,
      step3H3    = "Mount the component",
      step3Intro = "Create a Scala.js entry point that mounts the component into the DOM:",
      step4H3    = "Create index.html",
      step5H3    = "Run it",
      step5Intro = "# Compile with sbt",
      step5Outro =
        "Open http://localhost:5173 and click the button — the counter updates instantly without a page reload.",
      noSetupTitle = "No-setup option",
      noSetupText  = "Use the ",
      noSetupLink  = "Playground"
    ),

    components = GuideComponents(
      lead =
        "A Melt component is a .melt file with up to three sections: <script>, the template, and <style>. Together they describe the logic, markup, and appearance of a UI piece.",
      fileStructH2 = "File structure",
      propsH2      = "Props",
      propsIntro   =
        "Define component inputs with a case class Props inside the <script> block. Default values make all props optional:",
      propsAccess      = "Access props anywhere in the script and template via props:",
      usageH2          = "Using a component",
      usageIntro       = "Import and use components like HTML elements with a capital first letter:",
      childrenH2       = "Children (slot)",
      childrenIntro    = "Use the built-in children value to render nested content:",
      defaultsH2       = None,
      defaultsText     = None,
      scopedStylesH2   = "Scoped styles",
      scopedStylesText =
        "CSS written in a component's <style> block is automatically scoped to that component. A unique attribute is added to rendered elements so styles never leak to children or siblings.",
      noteTitle    = "Note",
      noteText     = "To apply styles globally, see the ",
      noteLinkText = "CSS guide"
    ),

    templateSyntax = GuideTemplateSyntax(
      lead =
        "The Melt template is standard HTML enriched with Scala expressions, directives, and event handlers. Everything inside {} is evaluated as Scala.",
      exprH2         = "Expressions",
      exprIntro      = "Embed any Scala expression inside {} in your template:",
      exprSignalText =
        "Expressions that evaluate to a Signal[A] or State[A] are automatically subscribed — the DOM updates whenever the value changes.",
      attrH2          = "Attribute binding",
      attrIntro       = "Use attr={expr} for dynamic attribute values:",
      twoWayH2        = "Two-way binding",
      twoWayIntro     = "bind:value creates a two-way link between a State[String] and an input element:",
      bindColH        = "Directive",
      bindTargetH     = "Targets",
      bindDescH       = "Description",
      bindValueDesc   = "Two-way string binding",
      bindCheckedDesc = "Two-way boolean binding",
      bindThisDesc    = "Captures the DOM element into a Ref",
      classH2         = "Class directives",
      classIntro      = "Toggle CSS classes reactively with class:name={signal}:",
      classMulti      = "Multiple class directives can be combined with a static class:",
      styleH2         = "Style directives",
      styleIntro      = "Set individual CSS properties reactively:",
      eventsH2        = "Event handlers",
      eventsIntro     = "Use on<event>={handler} to attach DOM event listeners:",
      spreadH2        = "Spread attributes",
      spreadIntro     = "Spread a map of attributes onto an element:",
      refH2           = "Element references",
      refIntro        = "Capture a DOM element with bind:this:",
      commentTitle    = None,
      commentText     = None
    ),

    reactivity = GuideReactivity(
      lead =
        "Melt's reactivity is built on two core types: State[A] (mutable) and Signal[A] (read-only derived). When a State changes, every part of the UI that reads it updates automatically.",
      stateH2       = "State",
      stateIntro    = "Create a mutable reactive value with State(initialValue):",
      stateReadText = "Read the current value with .value or by implicit conversion:",
      mutateH2      = "Mutating state",
      mutateIntro   = "Use .set(), .update(), or the built-in operators:",
      signalH2      = "Signal",
      signalIntro   = "A Signal[A] is a read-only view derived from one or more State values. Derive one with .map():",
      signalUsage   =
        "Signals update automatically whenever their source changes. Use them in templates the same way as State:",
      domH2    = "Reactive updates in the DOM",
      domIntro =
        "Any expression in a Melt template that reads a State or Signal is tracked. When the value changes, only that part of the DOM is updated — not the whole component.",
      calloutTitle = "No virtual DOM",
      calloutText  =
        "Melt does not diff trees. Each reactive binding is its own independent subscription. Changing one value updates exactly the DOM nodes that depend on it."
    ),

    computed = GuideComputed(
      lead =
        "Computed values are derived Signals that update automatically when their dependencies change. They are declared in the script section and used in the template just like State.",
      mapH2            = ".map() — transform a value",
      mapIntro         = "Use .map() to create a new signal from an existing one:",
      flatMapH2        = ".flatMap() — dynamic sources",
      flatMapIntro     = "Use .flatMap() when the derived value depends on another Signal:",
      memoH2           = ".memo() — deduplicate updates",
      memoIntro        = "Use .memo() to skip downstream updates when the computed value has not actually changed:",
      memoCalloutTitle = "When to use .memo()",
      memoCalloutText  =
        "Use .memo() when the mapped type has a cheap equality check but the parent changes frequently — for example, a boolean derived from an integer counter.",
      combineH2    = "Combining multiple signals",
      combineIntro = "Chain .map() calls or use .flatMap() to combine several reactive sources:"
    ),

    effects = GuideEffects(
      lead =
        "An effect is a side-effectful computation that re-runs whenever its declared dependencies change. Use effects for things like logging, network requests, and direct DOM manipulation.",
      basicH2    = "Basic effect",
      basicIntro =
        "Call effect(dep) { value => ... } inside the script section. The block runs once immediately with the current value, then re-runs in the post-DOM phase whenever the dependency changes:",
      multiH2    = "Multiple dependencies",
      multiIntro =
        "Pass multiple dependencies as arguments. The effect re-runs when any of them changes, receiving all current values at once:",
      cleanupH2    = "Cleanup",
      cleanupIntro =
        "Call onCleanup inside an effect to register a teardown function. It runs before each re-execution and once more when the component is destroyed:"
    ),

    events = GuideEvents(
      lead =
        "Event handlers in Melt are plain Scala functions attached directly to HTML elements with on<event>={handler} syntax.",
      basicH2    = "Basic handlers",
      basicOutro =
        "The handler receives the native DOM event as its argument. Use _ to ignore it when you don't need it.",
      eventObjH2      = "Accessing the event object",
      eventTableIntro = "Common event types from org.scalajs.dom:",
      handlerH        = "Handler",
      typeH           = "Event type",
      useH            = "Common use",
      row1Use         = "Buttons, links",
      row2Use         = "Text input changes",
      row3Use         = "Select, checkbox",
      row4Use         = "Form submission",
      row5Use         = "Key shortcuts",
      row6Use         = "Focus management",
      bindValueH2     = "bind:value shorthand",
      bindValueIntro  =
        "Instead of wiring oninput manually, use bind:value for a two-way sync between a text input and a State[String]:",
      windowH2    = "Window and body events",
      windowIntro = "Attach global listeners using <melt:window> and <melt:body> special elements (see ",
      windowOutro = None,
      customTitle = None,
      customText  = None
    ),

    lifecycle = GuideLifecycle(
      lead =
        "Melt components have a simple lifecycle: mount when inserted into the DOM and destroy when removed. You hook into these with onMount and Cleanup.",
      onMountH2    = "onMount",
      onMountIntro =
        "Code in onMount { ... } runs once, after the component's DOM has been inserted into the document:",
      ssrTitle     = "JVM (SSR) note",
      ssrText      = "onMount is a no-op on the JVM. It only runs in the browser.",
      cleanupH2    = "Cleanup on destroy",
      cleanupIntro =
        "Register teardown callbacks with onCleanup inside onMount. They run when the component is removed from the DOM:",
      effectCleanH2    = "Effect cleanup",
      effectCleanIntro =
        "onCleanup inside an effect block runs before each re-execution of the effect, and once more on component destroy:",
      destroyH2   = None,
      destroyText = None,
      warnTitle   = None,
      warnText    = None
    ),

    controlFlow = GuideControlFlow(
      lead =
        "Control flow in Melt templates uses Scala expressions directly — there are no special #if or #each directives. You write Scala inside {} and embed HTML elements within it.",
      condH2      = "Conditional rendering",
      condIntro   = "Use a Scala if expression. Map over a Signal to make it reactive:",
      whyMapTitle = "Why .map()?",
      whyMapText  =
        "Accessing loggedIn.value directly in a template expression reads the value once but does not subscribe to future changes. Wrapping with .map() creates a reactive subscription that updates the DOM automatically.",
      listH2        = "List rendering",
      listIntro     = "Render a list with Scala's .map() on a State[List[_]] or Signal[List[_]]:",
      keyedH2       = None,
      keyedText     = None,
      keyBlockH2    = "Key block",
      keyBlockIntro =
        "Force Melt to destroy and re-create a subtree when a key expression changes using the <melt:key> element. Useful for resetting component state:",
      keyBlockOutro = "Every time selectedId changes, DetailPanel is fully unmounted and remounted with fresh state.",
      emptyH2       = "Empty state",
      emptyIntro    = "Handle empty lists gracefully:"
    ),

    specialElements = GuideSpecialElements(
      lead =
        "Melt provides special built-in elements under the melt: namespace for common patterns that go beyond standard HTML.",
      headH2        = "<melt:head>",
      headIntro     = "Insert content into the <head> of the page from any component:",
      windowH2      = "<melt:window> / <melt:body>",
      windowIntro   = "Attach global event listeners without manually calling addEventListener:",
      windowOutro   = "Listeners are automatically removed when the component unmounts.",
      boundaryH2    = "<melt:boundary>",
      boundaryIntro = "Wrap a subtree in an error boundary that catches rendering errors and shows a fallback UI:",
      elementH2     = "<melt:element>",
      elementIntro  = "Render a dynamic tag name at runtime:",
      documentH2    = None,
      documentIntro = None,
      snippetsH2    = "Snippets and render",
      snippetsIntro = "Define reusable template fragments with {#snippet} and call them with {@render}:",
      tableElemH    = None,
      tableMountH   = None,
      tableUseH     = None
    ),

    transitions = GuideTransitions(
      lead =
        "Melt provides a reactive animation API for smooth value changes: Tween, Spring, and CSS-based transitions.",
      tweenH2    = "Tween",
      tweenIntro = "Smoothly interpolate a numeric value over time:",
      tweenOutro =
        "Tween animates a numeric value toward a target with set(target). Subscribe to changes with subscribe(fn) to update the DOM each frame.",
      springH2      = "Spring",
      springIntro   = "Use a physics-based spring for natural-feeling motion:",
      optionH       = "Option",
      defaultH      = "Default",
      descH         = "Description",
      stiffnessDesc = "How fast the spring moves toward the target",
      dampingDesc   = "How quickly oscillations decay (1.0 = no oscillation)",
      precisionDesc = "Distance at which motion stops",
      cssH2         = "CSS transitions",
      cssIntro      = "For class-based transitions, pair class: directives with CSS transition properties:",
      inOutH2       = None,
      inOutIntro    = None,
      perfTitle     = None,
      perfText      = None
    ),

    trustedHtml = GuideTrustedHtml(
      lead =
        "Melt escapes all dynamic content by default to prevent XSS attacks. When you need to inject raw HTML, wrap it in TrustedHtml to signal that you have reviewed the content.",
      whyH2    = "Why escaped by default?",
      whyIntro = "Consider this example:",
      whyOutro =
        "The template compiler automatically calls Escape.html on dynamic string values. You cannot accidentally render raw HTML.",
      unsafeH2    = "TrustedHtml.unsafe",
      unsafeIntro = "Use TrustedHtml.unsafe for HTML you control — static strings or content from a trusted CMS:",
      warnTitle   = "Never use with user input",
      warnText    =
        "Never pass untrusted user-supplied content to TrustedHtml.unsafe. Use a sanitizer library first, then wrap the sanitized result.",
      sanitizeH2      = "TrustedHtml.sanitize",
      sanitizeIntro   = "For user-generated content, provide a sanitizer function:",
      trustedUrlH2    = "TrustedUrl",
      trustedUrlIntro =
        "Melt also validates href and src attributes that accept URLs. Use TrustedUrl for dynamic values:",
      trustedUrlOutro =
        "Without wrapping, dangerous protocols (javascript:, vbscript:, data:text/html) are blocked at compile time.",
      secTableH2 = None
    ),

    css = GuideCss(
      lead =
        "CSS in Melt is scoped to the component by default. You can also use global styles, CSS custom properties, and optionally SCSS.",
      scopedH2    = "Scoped styles",
      scopedIntro =
        "Any CSS written inside a component's <style> block is automatically scoped. The compiler adds a unique attribute to each element, and prefixes every rule to match:",
      scopedGenText = "Generated HTML (simplified):",
      globalH2      = None,
      globalIntro   = None,
      dynamicH2     = "Dynamic styles",
      dynamicIntro  = "Use the style:property directive for reactive inline styles:",
      customH2      = "CSS custom properties",
      customIntro   = "Pass reactive values to CSS via custom properties:",
      scssH2        = "SCSS support",
      scssIntro     = "Add lang=\"scss\" to the style block and enable the SCSS preprocessor in your sbt config:",
      dartTitle     = "SCSS requires Dart Sass",
      dartText      =
        "The melt-sass-preprocessor module wraps Dart Sass. Add it to your JVM classpath and set meltStylePreprocessor := Some(SassPreprocessor) in your sbt config.",
      nestingH2    = None,
      nestingIntro = None
    ),

    testing = GuideTesting(
      lead =
        "Melt ships a melt-testkit module that lets you mount components in a simulated DOM environment and assert on the rendered output.",
      setupH2       = "Setup",
      setupIntro    = "Add the dependency to your test configuration:",
      writingH2     = "Writing a test",
      apiH2         = "MountedComponent API",
      methodH       = "Method",
      descH         = "Description",
      mountDesc     = "Mount a component and return a MountedComponent handle",
      textDesc      = "Get the text content of a matched element",
      clickDesc     = "Simulate a click on a matched element",
      inputDesc     = "Type a value into an input",
      existsDesc    = "Returns true if at least one element matches",
      findAllDesc   = "Find all matching elements",
      getByTextDesc = "Find element by text content",
      getByRoleDesc = "Find element by ARIA role",
      waitForDesc   = "Wait for async state changes",
      reactiveH2    = None,
      reactiveIntro = None,
      eventH2       = None,
      jvmTitle      = None,
      jvmText       = None,
      formH2        = "Testing form actions",
      formIntro     =
        "Form actions are tested at three layers, each with a helper so no HTTP server or real browser is needed.",
      formServerH3   = "Server: FormProbe",
      formServerDesc =
        "FormProbe(app) drives an app's routes in memory (reusing the http4s adapter, so real query parsing, the CSRF hook and action dispatch all run). submit(...) returns a ProbeResponse (status/body/location); origin and host can differ to emulate a cross-site attack.",
      formClientH3   = "Client: use:enhance",
      formClientDesc =
        "FetchStub installs a fetch that returns an EnhanceResult envelope (jsdom ships none); userEvent.submit fires the form's submit event. Together they exercise the enhance fetch and assert the reactive form state.",
      formCodecH3   = "Codecs",
      formCodecDesc =
        "FieldCodec[A].roundTrip(value) checks decode(encode(a)) == a, and FormDataDecoder[A].decode(FormData.parse(query)) decodes a raw body — assert with your own framework."
    ),

    routing = GuideRouting(
      lead =
        "MeltKit provides a type-safe routing DSL for full-stack Melt applications. Routes are declared in Scala, checked at compile time, and rendered on the server (SSR) or client (SPA).",
      setupH2         = "Setup",
      setupIntro      = "Add MeltKit to your JVM module:",
      routesH2        = "Defining routes",
      pathParamsH2    = "Path parameters",
      pathParamsIntro = "Declare parameters with param[T](\"name\") and combine them with /:",
      pathParamsOutro = None,
      ctxTableH2      = None,
      ctxMethodH      = None,
      ctxDescH        = None,
      ctxRenderDesc   = None,
      ctxHtmlDesc     = None,
      ctxParamsDesc   = None,
      ctxQueryDesc    = None,
      ctxLocalsDesc   = None,
      pageOptsH2      = "PageOptions",
      pageOptsIntro   = "Control SSR, CSR, and prerendering per route:",
      infoTitle       = None,
      infoText        = None
    ),

    ssr = GuideSsr(
      lead =
        "Server-Side Rendering (SSR) renders Melt components on the JVM and sends HTML to the browser. The client then hydrates the static HTML — attaching event listeners and making it interactive without re-rendering.",
      howH2 = "How it works",
      step1 = "The server receives a request.",
      step2 = "MeltKit renders the matching component to an HTML string on the JVM.",
      step3 = "The HTML is sent with hydration markers embedded.",
      step4 =
        "In the browser, the Scala.js bundle hydrates the DOM: existing nodes are reused and reactivity is attached.",
      enableH2    = "Enabling SSR",
      enableIntro = "Use the sbt-meltkit plugin and set the codegen mode:",
      routeH2     = "Route configuration",
      propsH2     = "Props serialization",
      propsIntro  =
        "For hydration to work, props are serialized to JSON by the server and deserialized by the client. Derive a PropsCodec automatically:",
      viteH2       = None,
      viteIntro    = None,
      partialTitle = "Partial hydration",
      partialText  = "Set csr = false to render a component as pure static HTML with no client-side JavaScript at all.",
      spaVsSsrH2   = None
    ),

    ssg = GuideSsg(
      lead =
        "Static Site Generation (SSG) pre-renders all pages at build time and outputs a directory of plain HTML files. The result can be served from any CDN with zero server infrastructure.",
      enableH2    = "Enabling prerender",
      enableIntro =
        "Set prerender = PrerenderOption.On on your routes and provide a list of all URL entries to generate:",
      runH2       = "Running the generator",
      runIntro    = "Create a generate main method that calls SsgGenerator.run:",
      runCmd      = "Run it with sbt:",
      outputH2    = "Output structure",
      deployH2    = None,
      deployIntro = None,
      deployLi1   = None,
      deployLi2   = None,
      deployLi3   = None,
      deployLi4   = None,
      dynTitle    = None,
      dynText     = None
    ),

    adapters = GuideAdapters(
      lead =
        "MeltKit adapters connect your app to a specific runtime environment. Choose the adapter that matches your deployment target.",
      http4sH2     = "http4s (JVM + Scala.js)",
      http4sIntro  = "The meltkit-adapter-http4s module integrates MeltKit with http4s for production JVM deployments:",
      nodeH2       = "Node.js",
      nodeIntro    = "Deploy to Node.js with meltkit-adapter-node:",
      browserH2    = "Browser (SPA)",
      browserIntro =
        "For pure client-side SPA without a server, use meltkit-adapter-browser. It handles client-side routing and history management:",
      cmpH2        = "Comparison",
      adapterH     = "Adapter",
      platformH    = "Platform",
      viaVite      = "via Vite",
      choiceH2     = None,
      choiceLi1Pre = None,
      choiceLi1Kit = None,
      choiceLi2Pre = None,
      choiceLi2Kit = None,
      choiceLi3Pre = None,
      choiceLi3Kit = None,
      choiceLi4Pre = None,
      multiTitle   = None,
      multiText    = None
    ),

    formActions = GuideFormActions(
      lead =
        "Form actions let a single declaration handle a form both ways: a plain POST that works with JavaScript disabled, and a fetch-based upgrade (use:enhance) that updates the page in place. The same server logic serves both.",
      howH2    = "Progressive enhancement",
      howIntro = "A form action is built on three layers, each a strict superset of the one below it:",
      step1    =
        "A plain <form method=\"post\"> submits natively and the server responds with a redirect (Post/Redirect/Get) or a re-render — this works with no JavaScript at all.",
      step2 =
        "Adding use:enhance intercepts the submit and replays it as a fetch, updating the form state in place with no full-page reload.",
      step3 =
        "The server action is written once; it detects an enhance request via a header and returns a JSON envelope instead of a redirect/HTML.",
      serverH2    = "Server: page actions",
      serverIntro =
        "Register a page with app.page. GET renders the page (form = None); POST runs an action that returns an ActionResult.",
      singleH3    = "Single default action",
      singleIntro =
        "For a one-form page, pass a single action = ctx => …. The submitted body is decoded with ctx.body.form[A]:",
      namedH3    = "Named actions",
      namedIntro =
        "For multiple submit buttons on one form (formaction=\"?/name\"), pass actions as a partial function over (actionName, ctx). All cases share the same form type:",
      resultH2    = "ActionResult",
      resultIntro =
        "An action returns an ActionResult, which drives both the native response and the enhance envelope:",
      resultSuccess = "Success(data) — re-render (native) or update the form (enhance).",
      resultFailure =
        "Failure(status, data), via the fail(status, data) helper — a validation failure carrying the form back with errors.",
      resultRedirect =
        "Redirect(location) — a 303 Post/Redirect/Get on the native path; a client-side navigation under enhance.",
      clientH2    = "Client: use:enhance",
      clientIntro =
        "Import meltkit.enhance and bind it with use:enhance={form}, where form is a Form primitive seeded from the hydration props. Removing use:enhance leaves a working native form — the progressive-enhancement floor.",
      nameOfTitle = "Type-safe field names",
      nameOfText  =
        "The input name is the one string not checked against the form type. Derive it from a selector: {...form.text(_.email)} spreads both the name and the seeded value, or name={form.nameOf(_.email)} for the name alone. A typo (_.emial) is a compile error, and the name always matches the exact field the server's FormDataDecoder reads.",
      controlsH2    = "All form controls",
      controlsIntro =
        "Every control has a type-checked spread helper that seeds it from the field: form.text (input), form.checkbox (checked from a Boolean), form.radio(_.f, option) and form.select/form.option (value + checked/selected on match). A <textarea> uses child interpolation for its content. All reuse the same FieldCodec:",
      customH2    = "Custom field types",
      customIntro =
        "Fields are decoded/encoded by a FieldCodec. String, Int, Long, Double, Boolean, Option and List are built in; add your own domain types by mapping an existing codec with imap/eimap. One FieldCodec drives both the server decode and the form.text value, so a custom type round-trips correctly. Nested case classes decode from hierarchical `field.subfield` keys, and nameOf/text accept nested selectors (form.nameOf(_.address.city)):",
      csrfH2    = "CSRF protection",
      csrfIntro =
        "Guard your actions against cross-site form submissions with the CSRF hook. For any state-changing form POST it requires the request Origin to match the server (rejecting others with 403); it covers both the native and the use:enhance submit, and loopback hosts default to http so it works in local development:",
      reactivityTitle = "Reactivity: pass State/Signal, not .value",
      reactivityText  =
        "Melt makes an attribute, list, or conditional reactive by OVERLOAD: pass a State/Signal (it subscribes) rather than a plain .value (read once). So use disabled={form.submitting} (not .value), and drive the error display from a conditional whose source is form.data — otherwise a validation Failure updates the state but the DOM never re-renders.",
      progressiveTitle = "Works without JavaScript",
      progressiveText  =
        "Because the native <form> path is the foundation, the form still submits and validates with JavaScript disabled. use:enhance is a pure upgrade — never a requirement."
    ),
    serverFunctions = GuideServerFunctions(
      lead =
        "A server function is a function you write once on the server and call directly from your components — as if it were local — even though it always runs on the server. Melt generates the HTTP endpoint and the client fetch for you and keeps the argument and result types in sync, so you never hand-write an endpoint, a fetch call, or a JSON codec just to move data between the browser and the server.",
      whatH2      = "What is a server function?",
      whatProblem =
        "Normally, getting data between the browser and the server means writing three things and keeping them in step by hand: an HTTP endpoint on the server, a fetch call on the client, and the request/response types. Rename one field and you fix it in three places — and nothing warns you when they drift apart.",
      whatModel =
        "A server function collapses that into one declaration. You define it once, call it like an ordinary function, and it runs on the server. This is the classic RPC (remote procedure call) idea: the call looks local but executes on the server, and the framework handles the network plumbing — the request is still there, just packaged as a function call.",
      whatServerOnly =
        "Because the body runs only on the server, it can freely use your database and secrets: they never reach the browser bundle. And because the contract is a single shared definition, the argument and result types cannot drift between the two sides.",
      whatKinds =
        "Melt has two kinds. A query reads data and gives you a reactive result with loading/error state; a command mutates data and is called from an event handler. Everything else — seeding, single-flight refresh, optimistic updates, per-field form issues — builds on those two.",
      contractH2    = "The contract",
      contractIntro =
        "Define the functions in a shared file, compiled for both the server and the client, so both sides agree on the same types. ServerFn.query is a read; ServerFn.command is a mutation. The string is the endpoint's logical name.",
      queryH2    = "query — reactive reads",
      queryIntro =
        "ServerFn.query defines a read. Calling it returns a Query whose state is a Signal[Async[Out]] (Loading / Done / Failed) rendered with a match. Seed it from a page-loader prop so SSR shows the data and the client hydrates with no loading flash or redundant fetch; refresh() re-runs it on demand.",
      commandH2    = "command — mutations",
      commandIntro =
        "ServerFn.command defines a mutation, called from an event handler as a Future. A non-2xx response fails the Future with a ServerFnException.",
      singleFlightH2    = "Single-flight",
      singleFlightIntro =
        "A mutation can refresh related queries in the same round-trip: dispatch(in).updates(query).run() runs the mutation and re-runs the requested queries on the server, piggybacking their new values on the response so the client updates them in one trip.",
      optimisticH2    = "Optimistic updates",
      optimisticIntro =
        "optimistic(query)(f) applies an expected change the moment run() fires — before the server responds — then reconciles with the authoritative value from the same round-trip on success, or rolls back automatically on failure.",
      issuesH2    = "Per-field form issues",
      issuesIntro =
        "A form model can carry per-field validation issues as errors: Map[String, List[String]]. On a validation failure the action returns the data with the map filled in, and the component shows each message next to its input — the SvelteKit fields.x.issues() equivalent.",
      invalidateH2    = "Refresh queries on form success",
      invalidateIntro =
        "Wire a form action back to your reactive queries with Form(…).invalidates(query). When the form submits successfully, each declared query refreshes so the list reflects the change — the SvelteKit invalidate-on-success / TanStack invalidateQueries pattern. It runs on the client only; on the server the refresh is a no-op.",
      serverOnlyTitle = "Server-only by construction",
      serverOnlyText  =
        "The implementation passed to app.serve only ever compiles on the server. On the JVM it can reference java.sql / secrets that Scala.js cannot compile at all, so a database client can never leak into the browser bundle — a structural guarantee, not a lint rule.",
      handlerTitle = "Commands run in event handlers",
      handlerText  =
        "dispatch / optimistic / run are client-only and belong inside event handlers, which are stripped from SSR output — so a shared component still compiles for the JVM. Queries (seeded / refresh) and the Async match render on both platforms. A .run() needs an ExecutionContext in scope (import scala.concurrent.ExecutionContext.Implicits.global)."
    )
  )

  val ja: GuideI18n = GuideI18n(
    nav = GuideNav(prev = "← 前のページ", next = "次のページ →"),

    introduction = GuideIntroduction(
      lead1         = "Melt は ",
      sfcFramework  = "Scala.js 向けの Single File Component (SFC) フレームワーク",
      lead2         = "です。Svelte にインスパイアされており、.melt ファイルにロジック・マークアップ・スタイルを記述すると、コンパイラが仮想 DOM なしの効率的な DOM 操作コードを生成します。",
      keyIdeasH2    = "3 つのコアアイデア",
      keyIdeasIntro = "Melt は以下の 3 つのシンプルな考え方を基盤としています。",
      idea1Bold     = "コンパイラが処理する。",
      idea1Text     = " リアクティビティはインポートするライブラリではなく、Melt コンパイラが生成コードに直接織り込みます。ランタイムのオーバーヘッドは最小限です。",
      idea2Bold     = "Scala の型がテンプレートを検査する。",
      idea2Text     = " {} 内の式はすべて本物の Scala コードです。scalac によるコンパイル時型チェックがテンプレートにも適用されるため、タイポや型の不一致はビルド時に検出されます。",
      idea3Bold     = "1 ソースで 3 ターゲット。",
      idea3Text     =
        " 同じ .melt ファイルが SPA (Scala.js による DOM コード)、SSR (JVM での HTML 文字列)、SSG (静的 HTML ファイル) の 3 形式にコンパイルされます。",
      firstLookH2    = "はじめての Melt コンポーネント",
      firstLookIntro = "インタラクティブなカウンターを 15 行で実装した例です。",
      in15Lines      = "この 15 行には次の要素が含まれています。",
      li15_1         = "ミュータブルなリアクティブ値 (State(0))",
      li15_2         = "自動更新される派生値 (count.map(_ * 2))",
      li15_3         = "状態を変更するイベントハンドラ (count += 1)",
      li15_4         = "このコンポーネントにのみ適用されるスコープ付き CSS",
      compilesH2     = "コンパイルパイプライン",
      compilesIntro  = "Melt コンパイラは .melt ファイルを次の手順で処理します。",
      step1          = "<script>・テンプレート・<style> セクションをパース",
      step2          = "セマンティックチェック（型ヒント・a11y 警告・セキュリティチェック）を実行",
      step3          = "テンプレート AST を内部 IR に変換",
      step4          = "SPA または SSR エミッターで Scala コードを生成",
      outputText     =
        "出力は通常の Scala オブジェクトです。scalac / Scala.js でそのままコンパイルできます。ブラウザには Melt ランタイムは存在せず、実際に使用するリアクティブプリミティブのみが含まれます。",
      calloutTitle = "今すぐ試してみましょう",
      calloutText  = "ブラウザ上でリアルタイムにコンパイルを確認できる ",
      calloutLink  = "Playground",
      notH2        = "Melt が「ではない」もの",
      not1Pre      = "フルスタックのメタフレームワーク単体ではありません（その役割は ",
      not1Kit      = "MeltKit",
      not1Post     = " が担います）。",
      not2         = "仮想 DOM を採用していません。更新は細粒度かつターゲットを絞って行われます。",
      not3         = "React・Vue・その他の JS フレームワークを必要としません。"
    ),

    installation = GuideInstallation(
      lead       = "Melt は sbt プラグイン経由で既存のプロジェクトに組み込めます。このページではゼロから必要なセットアップを順を追って説明します。",
      prereqH2   = "前提条件",
      prereq1    = "sbt 1.9 以上",
      prereq2    = "Scala 3.3.7 以上（コンパイラモジュール用。MeltKit は Scala 3.8 以上が必要）",
      prereq3    = "Node.js 18 以上（Vite の開発サーバーとバンドルに使用）",
      prereq4    = "JDK 17 以上",
      step1H2    = "1 · sbt プラグインを追加する",
      step1Intro = "project/plugins.sbt を作成または編集します。",
      step2H2    = "2 · build.sbt を設定する",
      step2Intro = "Scala.js モジュールでプラグインを有効化します。",
      step3H2    = "3 · ディレクトリ構成",
      step3Intro =
        "プラグインは src/main/scala（または設定したソースディレクトリ）以下の .melt ファイルを自動検出します。生成された Scala ソースは target/scala-3.x.x/src_managed/main/melt/ に配置され、通常の Scala ファイルと一緒にコンパイルされます。",
      step3Layout   = "典型的なレイアウト",
      step4H2       = "4 · Vite の設定（任意）",
      step4Intro    = "Melt はどのバンドラーとも連携できますが、開発には Vite が最適です。プロジェクトルートに vite.config.mjs を作成します。",
      snapshotTitle = "SNAPSHOT リリースについて",
      snapshotText  =
        "Melt は現在活発に開発中です。依存関係が見つからない場合は、project/repositories または build.sbt に Sonatype スナップショットリポジトリを追加してください。",
      nextStepTitle = Some("次のステップ"),
      nextStepText  = Some("セットアップが完了したら "),
      nextStepLink  = Some("クイックスタート")
    ),

    quickStart = GuideQuickStart(
      lead         = "このガイドでは、カウンターコンポーネントを 5 分以内でゼロから動かすところまで解説します。",
      step1H3      = "プロジェクトを作成する",
      step1Text    = "Melt のカウンターサンプルをベースにするか、プラグインを有効化した最小限の sbt プロジェクトを作成します（",
      step1Link    = "インストール",
      step2H3      = "最初のコンポーネントを書く",
      step2Intro   = "src/main/scala/Counter.melt を作成します。",
      step2Outro   = Some("コンポーネントは <script>・テンプレート・<style> の 3 セクションで構成されます。"),
      step3H3      = "コンポーネントをマウントする",
      step3Intro   = "Scala.js のエントリポイントを作成して DOM にコンポーネントをマウントします。",
      step4H3      = "index.html を作成する",
      step5H3      = "実行する",
      step5Intro   = "# sbt でコンパイル",
      step5Outro   = "http://localhost:5173 を開いてボタンをクリックしてみてください。ページのリロードなしにカウンターが即座に更新されます。",
      noSetupTitle = "インストール不要で試したい場合",
      noSetupText  = "",
      noSetupLink  = "Playground"
    ),

    components = GuideComponents(
      lead =
        "Melt コンポーネントは .melt ファイルで構成され、<script>・テンプレート・<style> の最大 3 つのセクションを持ちます。ロジック・マークアップ・スタイルを 1 ファイルにまとめることで、関心事が自然にまとまります。",
      fileStructH2     = "ファイル構造",
      propsH2          = "Props の定義",
      propsIntro       = "コンポーネントへの入力は <script> ブロック内に case class Props として定義します。デフォルト値を設定することで、すべての Props を省略可能にできます。",
      propsAccess      = "Props はスクリプトおよびテンプレートのどこからでも props 経由でアクセスできます。",
      usageH2          = "コンポーネントの利用",
      usageIntro       = "コンポーネントはインポートして HTML タグ（大文字始まり）として使います。",
      childrenH2       = "children スロット",
      childrenIntro    = "組み込みの children 値を使うと、ネストされたコンテンツをレンダリングできます。",
      defaultsH2       = Some("Props のデフォルト値"),
      defaultsText     = Some("デフォルト値を設定した Props は省略して呼び出すことができます。コンポーネントの API を使いやすく保ちつつ、必要なときだけカスタマイズできる設計が可能です。"),
      scopedStylesH2   = "スコープ付きスタイル",
      scopedStylesText =
        "コンポーネントの <style> ブロックに書かれた CSS は、自動的にそのコンポーネントにスコープされます。コンパイラがレンダリングされた要素に一意の属性を付与するため、スタイルが子コンポーネントや兄弟要素に漏れることはありません。",
      noteTitle    = "グローバルスタイルについて",
      noteText     = "スタイルをグローバルに適用したい場合は ",
      noteLinkText = "CSS ガイド"
    ),

    templateSyntax = GuideTemplateSyntax(
      lead            = "Melt のテンプレートは、Scala 式・ディレクティブ・イベントハンドラで拡張された標準 HTML です。{} 内はすべて Scala として評価されます。",
      exprH2          = "テキスト補間",
      exprIntro       = "テンプレートの任意の場所で {} を使って Scala 式を埋め込めます。",
      exprSignalText  = "Signal[A] や State[A] を返す式は自動的にサブスクライブされ、値が変わると DOM が更新されます。",
      attrH2          = "属性バインディング",
      attrIntro       = "動的な属性値には attr={expr} を使います。",
      twoWayH2        = "双方向バインディング",
      twoWayIntro     = "bind:value は State[String] と input 要素を双方向に結びつけます。",
      bindColH        = "ディレクティブ",
      bindTargetH     = "対象",
      bindDescH       = "説明",
      bindValueDesc   = "文字列の双方向バインディング",
      bindCheckedDesc = "Boolean の双方向バインディング",
      bindThisDesc    = "DOM 要素を Ref に格納する",
      classH2         = "class ディレクティブ",
      classIntro      = "class:name={signal} で CSS クラスをリアクティブに切り替えます。",
      classMulti      = "静的な class と組み合わせることもできます。",
      styleH2         = "style ディレクティブ",
      styleIntro      = "個別の CSS プロパティをリアクティブに設定します。",
      eventsH2        = "イベントハンドラ",
      eventsIntro     = "on<event>={handler} で DOM イベントリスナーを設定します。",
      spreadH2        = "スプレッド属性",
      spreadIntro     = "属性のマップを要素に展開できます。",
      refH2           = "要素参照",
      refIntro        = "bind:this で DOM 要素をキャプチャします。",
      commentTitle    = Some("コメントについて"),
      commentText     = Some("テンプレート内で HTML コメント <!-- ... --> は通常通り記述できます。コンパイラはコメントを無視してコード生成します。")
    ),

    reactivity = GuideReactivity(
      lead =
        "Melt のリアクティビティは State[A]（ミュータブル）と Signal[A]（読み取り専用の派生値）という 2 つの核心型で構成されます。State が変わると、それを読んでいる UI の部分が自動更新されます。",
      stateH2       = "State を作成する",
      stateIntro    = "State(initialValue) でミュータブルなリアクティブ値を作成します。",
      stateReadText = "現在の値は .value または暗黙変換で読み取れます。",
      mutateH2      = "State を更新する",
      mutateIntro   = ".set()、.update()、または組み込み演算子を使います。",
      signalH2      = "Signal — 読み取り専用の派生値",
      signalIntro   = "Signal[A] は 1 つ以上の State から派生した読み取り専用のビューです。.map() で作成します。",
      signalUsage   = "Signal は元の値が変わると自動更新されます。テンプレートでは State と同じように使います。",
      domH2         = "DOM のリアクティブ更新",
      domIntro      =
        "Melt テンプレートで State や Signal を読み取る式はすべてトラッキングされます。値が変わると、その式に対応する DOM ノードだけが更新されます。コンポーネント全体を再レンダリングすることはありません。",
      calloutTitle = "仮想 DOM なし",
      calloutText  = "Melt はツリーの差分計算を行いません。各リアクティブバインディングが独立したサブスクリプションです。1 つの値を変更すると、それに依存する DOM ノードだけが更新されます。"
    ),

    computed = GuideComputed(
      lead             = "算出値（Computed values）は依存関係が変わると自動更新される派生 Signal です。スクリプトセクションで宣言し、State と同じようにテンプレートで利用します。",
      mapH2            = ".map() — 値を変換する",
      mapIntro         = "既存の Signal から新しい Signal を作成するには .map() を使います。",
      flatMapH2        = ".flatMap() — 動的なソース切り替え",
      flatMapIntro     = "派生値が別の Signal に依存するときは .flatMap() を使います。",
      memoH2           = ".memo() — 重複更新を抑制する",
      memoIntro        = "計算した値が実際には変わっていないときにも下流の更新が走ることがあります。.memo() を使うと値が同じ場合は更新をスキップできます。",
      memoCalloutTitle = ".memo() を使うべきタイミング",
      memoCalloutText  = "マップ先の型が安価な等値チェックを持ち、かつ親が頻繁に変化する場合に有効です。例えば、整数カウンターから導出した Boolean フラグなどが典型例です。",
      combineH2        = "複数の Signal を組み合わせる",
      combineIntro     = ".map() を連鎖させるか、.flatMap() を使って複数のリアクティブソースを結合します。"
    ),

    effects = GuideEffects(
      lead       = "エフェクトは、宣言した依存関係が変化するたびに再実行される副作用を伴う処理です。ログ出力・ネットワークリクエスト・直接的な DOM 操作などに使います。",
      basicH2    = "基本的なエフェクト",
      basicIntro =
        "スクリプトセクション内で effect(dep) { value => ... } を呼び出します。ブロックはマウント時に現在の値ですぐ実行され、依存関係が変わるたびに DOM 更新後に再実行されます。",
      multiH2      = "複数の依存関係",
      multiIntro   = "複数の依存関係を引数に渡せます。いずれかが変化すると、全ての現在値を受け取って再実行されます。",
      cleanupH2    = "クリーンアップ",
      cleanupIntro = "エフェクト内で onCleanup を呼ぶと、再実行前とコンポーネント破棄時にクリーンアップ関数が実行されます。タイマーやイベントリスナーの解除に使います。"
    ),

    events = GuideEvents(
      lead            = "Melt のイベントハンドラは、on<event>={handler} 構文で HTML 要素に直接取り付けるプレーンな Scala 関数です。",
      basicH2         = "基本的なハンドラ",
      basicOutro      = "ハンドラはネイティブの DOM イベントを引数として受け取ります。イベントが不要な場合は _ で無視できます。",
      eventObjH2      = "イベントオブジェクトへのアクセス",
      eventTableIntro = "org.scalajs.dom が提供する代表的なイベント型",
      handlerH        = "ハンドラ",
      typeH           = "イベント型",
      useH            = "主な用途",
      row1Use         = "ボタン・リンク",
      row2Use         = "テキスト入力の変化",
      row3Use         = "select・チェックボックス",
      row4Use         = "フォーム送信",
      row5Use         = "キーショートカット",
      row6Use         = "フォーカス管理",
      bindValueH2     = "bind:value ショートハンド",
      bindValueIntro  = "oninput を手動で配線する代わりに bind:value を使えば、テキスト入力と State[String] の双方向同期が簡単に実現できます。",
      windowH2        = "Window・Body のグローバルイベント",
      windowIntro     = "melt:window や melt:body 特殊要素でグローバルリスナーを設定します（",
      windowOutro     = Some("コンポーネントがアンマウントされると、リスナーは自動的に削除されます。"),
      customTitle     = Some("カスタムイベント"),
      customText      = Some("子コンポーネントから親へ通知を送りたい場合は、Props にコールバック関数 onXxx: () => Unit を定義するのが Melt での推奨パターンです。")
    ),

    lifecycle = GuideLifecycle(
      lead         = "Melt コンポーネントのライフサイクルはシンプルです。DOM に挿入されたとき（マウント）と削除されたとき（デストロイ）の 2 つのタイミングにフックできます。",
      onMountH2    = "onMount",
      onMountIntro =
        "onMount { ... } 内のコードは、コンポーネントの DOM がドキュメントに挿入された後に一度だけ実行されます。DOM のサイズ計測やキャンバスへの描画など、実際に DOM が存在しないとできない処理をここに書きます。",
      ssrTitle         = "JVM (SSR) での注意",
      ssrText          = "onMount は JVM 上では no-op です。ブラウザでのみ実行されます。",
      cleanupH2        = "Cleanup — デストロイ時のクリーンアップ",
      cleanupIntro     = "onCleanup でティアダウンコールバックを登録できます。コンポーネントが DOM から削除されたときに実行されます。",
      effectCleanH2    = "Effect 内のクリーンアップ",
      effectCleanIntro = "effect ブロック内で onCleanup を使うと、エフェクトが再実行される直前と、コンポーネントのデストロイ時に呼ばれます。",
      destroyH2        = Some("Lifecycle.destroyTree()"),
      destroyText      =
        Some("コンポーネントツリーを手動でデストロイしたい場合は Lifecycle.destroyTree(root) を呼び出します。通常は Melt が内部的に管理するため、明示的に呼ぶ機会は少ないです。"),
      warnTitle = Some("注意事項"),
      warnText  = Some("onMount は非同期処理の完了を待ちません。非同期処理が必要な場合は onMount 内で Future や Promise を扱い、完了後に State を更新してください。")
    ),

    controlFlow = GuideControlFlow(
      lead = "Melt テンプレートの制御フローは Scala 式を直接使います。特殊な #if や #each ディレクティブはありません。{} の中に Scala を書き、その中に HTML 要素を埋め込む形式です。",
      condH2      = "条件付きレンダリング",
      condIntro   = "Scala の if 式を使います。リアクティブにするには Signal を .map() します。",
      whyMapTitle = ".map() が必要な理由",
      whyMapText  =
        "テンプレート式で loggedIn.value を直接読むと、その時点の値を一度だけ読み取るだけでその後の変化には追従しません。.map() でラップすることで、値が変わるたびに DOM が自動更新されるリアクティブなサブスクリプションになります。",
      listH2        = "リスト描画",
      listIntro     = "Scala の .map() を State[List[_]] や Signal[List[_]] に使ってリストをレンダリングします。",
      keyedH2       = Some("キー付きリスト"),
      keyedText     = Some("リスト要素に key 属性を付けると、Melt は要素の追加・削除・並べ替え時に既存 DOM ノードを再利用して効率よく更新できます。"),
      keyBlockH2    = "melt:key ブロック",
      keyBlockIntro = "キー式が変わるとサブツリーを完全に破棄・再作成したい場合は melt:key 要素を使います。コンポーネントの状態をリセットするのに便利です。",
      keyBlockOutro = "selectedId が変わるたびに DetailPanel がアンマウントされ、初期状態でマウントし直されます。",
      emptyH2       = "空の状態を扱う",
      emptyIntro    = "リストが空の場合のフォールバック表示も簡単に書けます。"
    ),

    specialElements = GuideSpecialElements(
      lead          = "Melt は melt: 名前空間の下に、標準 HTML を超えたよくあるパターン向けの特殊組み込み要素を提供しています。",
      headH2        = "<melt:head>",
      headIntro     = "任意のコンポーネントからページの <head> にコンテンツを差し込めます。タイトルやメタタグの動的設定に使います。",
      windowH2      = "<melt:window> / <melt:body>",
      windowIntro   = "addEventListener を手動で呼ばずにグローバルイベントリスナーを設定できます。コンポーネントがアンマウントされると自動的に削除されます。",
      windowOutro   = "",
      boundaryH2    = "<melt:boundary>",
      boundaryIntro = "サブツリーをエラーバウンダリでラップして、レンダリングエラーをキャッチしフォールバック UI を表示します。非同期コンポーネントの pending/failed 状態にも対応します。",
      elementH2     = "<melt:element>",
      elementIntro  = "実行時に動的なタグ名をレンダリングします。見出しレベル (h1〜h6) の動的変更などに便利です。",
      documentH2    = Some("<melt:document>"),
      documentIntro = Some("ドキュメントレベルのイベントリスナーを設定します。melt:window と似ていますが、document オブジェクトに設定されます。"),
      snippetsH2    = "スニペットと render",
      snippetsIntro = "再利用可能なテンプレートフラグメントを {#snippet} で定義し、{@render} で呼び出せます。",
      tableElemH    = Some("要素"),
      tableMountH   = Some("マウント先"),
      tableUseH     = Some("主な用途")
    ),

    transitions = GuideTransitions(
      lead          = "Melt は値の変化をなめらかにアニメーションする Tween・Spring と、CSS ベースのトランジションをサポートします。",
      tweenH2       = "Tween — 数値を時間補間する",
      tweenIntro    = "数値をスムーズに変化させるには Tween を使います。値が変わると、設定した時間をかけて目標値に向かってアニメーションします。",
      tweenOutro    = "Tween は数値を目標値に向けてアニメーションします。set(target) でアニメーション開始、subscribe(fn) でフレームごとの値変化を受け取ります。",
      springH2      = "Spring — 物理ベースのアニメーション",
      springIntro   = "自然な動きを実現したい場合は物理ベースのバネモデル Spring を使います。",
      optionH       = "オプション",
      defaultH      = "デフォルト",
      descH         = "説明",
      stiffnessDesc = "バネの硬さ — 目標値に近づく速さ",
      dampingDesc   = "減衰係数 — 振動の収まる速さ (1.0 = 振動なし)",
      precisionDesc = "動きが止まるとみなす距離",
      cssH2         = "CSS トランジション",
      cssIntro      = "クラスベースのトランジションには class: ディレクティブと CSS の transition プロパティを組み合わせます。",
      inOutH2       = Some("in: / out: で入退場を個別指定"),
      inOutIntro    = Some("要素の表示・非表示に異なるトランジションを指定したい場合は、in: と out: ディレクティブを使います。"),
      perfTitle     = Some("パフォーマンスのヒント"),
      perfText      =
        Some("アニメーションには opacity や transform のような GPU でアクセラレーションされるプロパティを優先して使うと、スムーズな 60fps アニメーションが実現しやすくなります。")
    ),

    trustedHtml = GuideTrustedHtml(
      lead =
        "Melt はデフォルトですべての動的コンテンツをエスケープして XSS 攻撃を防ぎます。生の HTML を挿入する必要がある場合は、コンテンツを確認済みであることを示す TrustedHtml でラップします。",
      whyH2           = "なぜデフォルトでエスケープするのか",
      whyIntro        = "次の例を見てください。",
      whyOutro        = "テンプレートコンパイラは動的な文字列値に自動的に Escape.html を適用します。生の HTML を誤ってレンダリングすることはできない設計になっています。",
      unsafeH2        = "TrustedHtml.unsafe",
      unsafeIntro     = "自分がコントロールしている HTML — 静的文字列や信頼済みの CMS コンテンツ — には TrustedHtml.unsafe を使います。",
      warnTitle       = "ユーザー入力には絶対に使わない",
      warnText        = "ユーザーが入力した信頼できないコンテンツを TrustedHtml.unsafe に渡さないでください。サニタイザーライブラリで処理してからラップしてください。",
      sanitizeH2      = "TrustedHtml.sanitize",
      sanitizeIntro   = "ユーザー生成コンテンツには、サニタイザー関数を受け取る TrustedHtml.sanitize を使います。",
      trustedUrlH2    = "TrustedUrl",
      trustedUrlIntro = "Melt は href や src などの URL 属性も検証します。動的な値には TrustedUrl を使います。",
      trustedUrlOutro = "ラップなしで渡すと、危険なプロトコル（javascript:、vbscript:、data:text/html）はコンパイル時にブロックされます。",
      secTableH2      = Some("セキュリティチェック一覧")
    ),

    css = GuideCss(
      lead          = "Melt の CSS はデフォルトでコンポーネントにスコープされます。グローバルスタイル・CSS カスタムプロパティ・SCSS も利用できます。",
      scopedH2      = "スコープ付きスタイル",
      scopedIntro   = "コンポーネントの <style> ブロックに書かれた CSS は自動的にスコープされます。コンパイラが各要素に一意の属性を付与し、すべてのルールにプレフィックスを追加します。",
      scopedGenText = "生成される HTML（簡略化）",
      globalH2      = Some(":global() でグローバルスタイル"),
      globalIntro   = Some("スコープを外して特定のルールをグローバルに適用したい場合は :global() を使います。"),
      dynamicH2     = "動的スタイル",
      dynamicIntro  = "style: ディレクティブでリアクティブなインラインスタイルを設定します。",
      customH2      = "CSS カスタムプロパティ（変数）",
      customIntro   = "CSS 変数を使ってリアクティブな値を CSS に渡せます。",
      scssH2        = "SCSS サポート",
      scssIntro     = "style ブロックに lang=\"scss\" を追加し、sbt の設定で SCSS プリプロセッサを有効化します。",
      dartTitle     = "SCSS には Dart Sass が必要です",
      dartText      =
        "melt-sass-preprocessor モジュールが Dart Sass をラップしています。JVM のクラスパスに追加し、sbt の設定に meltStylePreprocessor := Some(SassPreprocessor) を追記してください。",
      nestingH2    = Some("CSS Nesting"),
      nestingIntro = Some("Melt の CSS パーサーは CSS Nesting 仕様をサポートしています。SCSS なしでも入れ子のルールが書けます。")
    ),

    testing = GuideTesting(
      lead          = "Melt は melt-testkit モジュールを提供しており、シミュレートされた DOM 環境でコンポーネントをマウントしてレンダリング結果をアサートできます。",
      setupH2       = "セットアップ",
      setupIntro    = "テスト設定に依存関係を追加します。",
      writingH2     = "テストを書く",
      apiH2         = "MountedComponent API",
      methodH       = "メソッド",
      descH         = "説明",
      mountDesc     = "コンポーネントをマウントして MountedComponent ハンドルを返す",
      textDesc      = "マッチした要素のテキストコンテンツを取得",
      clickDesc     = "マッチした要素のクリックをシミュレート",
      inputDesc     = "input に値を入力",
      existsDesc    = "要素が存在するか確認",
      findAllDesc   = "マッチするすべての要素を返す",
      getByTextDesc = "テキストコンテンツで要素を検索",
      getByRoleDesc = "ARIA ロールで要素を検索",
      waitForDesc   = "非同期の状態変化を待機",
      reactiveH2    = Some("リアクティブな状態のアサーション"),
      reactiveIntro = Some("State を直接変更してレンダリング結果を確認することもできます。"),
      eventH2       = Some("イベントのシミュレーション"),
      jvmTitle      = Some("テストは Node.js で実行される"),
      jvmText   = Some("testkit は Node.js 上で jsdom を使って動作するため、実ブラウザなしで高速にテストできます。DOM 操作のシミュレーションは testkit が内部的に処理します。"),
      formH2    = "フォームアクションのテスト",
      formIntro = "フォームアクションは 3 つの層でテストでき、それぞれヘルパがあるので HTTP サーバも実ブラウザも不要です。",
      formServerH3   = "サーバ: FormProbe",
      formServerDesc =
        "FormProbe(app) はアプリの routes をインメモリで実行します（http4s アダプタを再利用するので、実クエリ解析・CSRF フック・アクションのディスパッチがすべて走ります）。submit(...) は ProbeResponse（status/body/location）を返し、origin と host を別々に指定してクロスサイト攻撃を再現できます。",
      formClientH3   = "クライアント: use:enhance",
      formClientDesc =
        "FetchStub は EnhanceResult エンベロープを返す fetch を設置し（jsdom は fetch を持たない）、userEvent.submit がフォームの submit を発火します。両者で enhance の fetch を動かし、リアクティブなフォーム状態を検証します。",
      formCodecH3   = "コーデック",
      formCodecDesc =
        "FieldCodec[A].roundTrip(value) は decode(encode(a)) == a を検証し、FormDataDecoder[A].decode(FormData.parse(query)) は生のボディをデコードします。アサーションは各自のフレームワークで行います。"
    ),

    routing = GuideRouting(
      lead =
        "MeltKit はフルスタックの Melt アプリケーション向けに型安全なルーティング DSL を提供します。ルートは Scala で宣言され、コンパイル時に検査され、サーバー (SSR) またはクライアント (SPA) でレンダリングされます。",
      setupH2         = "セットアップ",
      setupIntro      = "JVM モジュールに MeltKit を追加します。",
      routesH2        = "ルートを定義する",
      pathParamsH2    = "パスパラメータ",
      pathParamsIntro = "param[T](\"name\") でパラメータを宣言し、/ で連結します。",
      pathParamsOutro =
        Some("パラメータの型は Scala の型システムで検査されます。param[Int](\"page\") と宣言すれば、ctx.params.page は Int として型安全に取得できます。"),
      ctxTableH2    = Some("ctx でレスポンスを構築する"),
      ctxMethodH    = Some("メソッド"),
      ctxDescH      = Some("説明"),
      ctxRenderDesc = Some("コンポーネントを HTML にレンダリングしてレスポンスを返す"),
      ctxHtmlDesc   = Some("プレーンテキストの文字列でレスポンスを返す"),
      ctxParamsDesc = Some("パスパラメータへのアクセス"),
      ctxQueryDesc  = Some("クエリパラメータへのアクセス"),
      ctxLocalsDesc = Some("リクエストスコープのストレージ"),
      pageOptsH2    = "PageOptions",
      pageOptsIntro = "ルートごとに SSR・CSR・プリレンダリングを制御します。",
      infoTitle     = Some("詳細は SSR/SSG ガイドを参照"),
      infoText      = Some("SSR の仕組みについては サーバーサイドレンダリング、静的ページ生成については 静的サイト生成 を参照してください。")
    ),

    ssr = GuideSsr(
      lead =
        "サーバーサイドレンダリング (SSR) は Melt コンポーネントを JVM でレンダリングして HTML をブラウザに送ります。その後クライアント側でハイドレーションが行われ、静的な HTML に対してイベントリスナーが設定されてインタラクティブになります。",
      howH2       = "仕組み",
      step1       = "サーバーがリクエストを受け取る",
      step2       = "MeltKit が JVM 上でマッチするコンポーネントを HTML 文字列にレンダリング",
      step3       = "ハイドレーションマーカーを埋め込んだ HTML をブラウザに送信",
      step4       = "ブラウザで Scala.js バンドルがハイドレーション: 既存の DOM ノードを再利用してリアクティビティを設定",
      enableH2    = "SSR を有効にする",
      enableIntro = "sbt-meltkit プラグインを使ってコードジェネレーションモードを設定します。",
      routeH2     = "ルート設定",
      propsH2     = "Props のシリアライズ",
      propsIntro  = "ハイドレーションを機能させるために、Props はサーバーで JSON にシリアライズされ、クライアントでデシリアライズされます。ケースクラスに対しては自動的にコーデックが導出されます。",
      viteH2      = Some("Vite の設定"),
      viteIntro   = Some(
        "SSR + ハイドレーションのプロダクションビルドでは、Rollup のデフォルト設定が named export (hydrate) を削除してしまうことがあります。vite.config.mjs に以下を追加してください。"
      ),
      partialTitle = "部分ハイドレーション",
      partialText  = "csr = false を設定すると、クライアント側の JavaScript を一切使わない純粋な静的 HTML としてコンポーネントをレンダリングできます。",
      spaVsSsrH2   = Some("SSR と SPA の違い")
    ),

    ssg = GuideSsg(
      lead        = "静的サイト生成 (SSG) はビルド時にすべてのページをプリレンダリングしてプレーンな HTML ファイルのディレクトリを出力します。サーバーインフラなしで CDN から配信できます。",
      enableH2    = "プリレンダリングを有効にする",
      enableIntro = "ルートに prerender = PrerenderOption.On を設定し、生成するすべての URL エントリを指定します。",
      runH2       = "ジェネレーターを実行する",
      runIntro    = "SsgGenerator.run を呼ぶ generate メイン関数を作成します。",
      runCmd      = "sbt から実行します。",
      outputH2    = "出力ディレクトリ構成",
      deployH2    = Some("デプロイ方法"),
      deployIntro = Some("生成された dist/ ディレクトリをそのまま任意の静的ホスティングサービスにアップロードするだけです。"),
      deployLi1   = Some("GitHub Pages: dist/ ブランチを公開"),
      deployLi2   = Some("Netlify / Vercel: dist を publish directory に指定"),
      deployLi3   = Some("Cloudflare Pages: dist ディレクトリを直接デプロイ"),
      deployLi4   = Some("S3 + CloudFront: dist/ を S3 にアップロードして CloudFront で配信"),
      dynTitle    = Some("動的ページも静的生成できる"),
      dynText     = Some("entries に動的ページのすべての URL を列挙することで、パスパラメータを持つルートも静的生成できます。例えばブログ記事のスラッグ一覧をデータベースから取得して渡せます。")
    ),

    adapters = GuideAdapters(
      lead         = "MeltKit アダプターはアプリを特定のランタイム環境に接続します。デプロイターゲットに合ったアダプターを選択してください。",
      http4sH2     = "http4s アダプター（JVM）",
      http4sIntro  = "meltkit-adapter-http4s モジュールは MeltKit を http4s と統合し、JVM でのプロダクション運用を可能にします。",
      nodeH2       = "Node.js アダプター",
      nodeIntro    = "Node.js にデプロイする場合は meltkit-adapter-node を使います。SSR と SSG の両方に対応しています。",
      browserH2    = "ブラウザ SPA アダプター",
      browserIntro = "サーバーなしの純粋なクライアントサイド SPA には meltkit-adapter-browser を使います。クライアントサイドルーティングと履歴管理を担当します。",
      cmpH2        = "アダプター比較",
      adapterH     = "アダプター",
      platformH    = "プラットフォーム",
      viaVite      = "Vite 経由",
      choiceH2     = Some("アダプターの選び方"),
      choiceLi1Pre = Some("既に "),
      choiceLi1Kit = Some(" を使っている → meltkit-adapter-http4s"),
      choiceLi2Pre = Some(""),
      choiceLi2Kit = Some(" エコシステムで動かしたい → meltkit-adapter-node"),
      choiceLi3Pre = Some("サーバーなしの "),
      choiceLi3Kit = Some(" を作りたい → meltkit-adapter-browser"),
      choiceLi4Pre = Some(""),
      multiTitle   = Some("複数アダプターの組み合わせ"),
      multiText    = Some("開発時は Node.js アダプターで素早く起動し、プロダクションでは http4s にデプロイするような環境別の切り替えも可能です。")
    ),

    formActions = GuideFormActions(
      lead =
        "フォームアクションは、1 つの宣言でフォームを両方の形で扱えるようにします。JavaScript 無効でも動くネイティブ POST と、その場でページを更新する fetch ベースの拡張（use:enhance）です。同じサーバロジックが両方に対応します。",
      howH2    = "プログレッシブエンハンスメント",
      howIntro = "フォームアクションは 3 つの層で構成され、上の層は下の層の厳密な上位集合です。",
      step1    =
        "素の <form method=\"post\"> はネイティブに送信され、サーバはリダイレクト（Post/Redirect/Get）または再描画で応答します。これは JavaScript が一切なくても動作します。",
      step2       = "use:enhance を付けると submit を横取りして fetch として再送し、ページ全体をリロードせずにフォーム状態をその場で更新します。",
      step3       = "サーバのアクションは 1 回だけ書きます。ヘッダで enhance リクエストを検知し、リダイレクト／HTML の代わりに JSON エンベロープを返します。",
      serverH2    = "サーバ: ページアクション",
      serverIntro = "app.page でページを登録します。GET はページを描画し（form = None）、POST は ActionResult を返すアクションを実行します。",
      singleH3    = "単一のデフォルトアクション",
      singleIntro = "1 フォームのページなら、単一の action = ctx => … を渡します。送信ボディは ctx.body.form[A] でデコードします。",
      namedH3     = "名前付きアクション",
      namedIntro  =
        "1 つのフォームに複数の submit ボタン（formaction=\"?/name\"）がある場合、actions を (アクション名, ctx) のタプルに対する部分関数として渡します。すべてのケースは同じフォーム型を共有します。",
      resultH2       = "ActionResult",
      resultIntro    = "アクションは ActionResult を返し、これがネイティブ応答と enhance エンベロープの両方を決定します。",
      resultSuccess  = "Success(data) — 再描画（ネイティブ）またはフォーム更新（enhance）。",
      resultFailure  = "Failure(status, data)（fail(status, data) ヘルパー経由）— バリデーション失敗。エラー付きでフォームを返します。",
      resultRedirect = "Redirect(location) — ネイティブ経路では 303 Post/Redirect/Get、enhance ではクライアント側ナビゲーション。",
      clientH2       = "クライアント: use:enhance",
      clientIntro    =
        "meltkit.enhance をインポートし、use:enhance={form} でバインドします。form は hydration の props から生成した Form プリミティブです。use:enhance を外せば動作するネイティブフォームが残ります（プログレッシブエンハンスメントの土台）。",
      nameOfTitle = "型安全なフィールド名",
      nameOfText  =
        "input の name はフォーム型と照合されない唯一の文字列です。セレクタから導出しましょう。{...form.text(_.email)} は name と初期 value をまとめて展開し、name={form.nameOf(_.email)} は name だけを返します。タイポ（_.emial）はコンパイルエラーになり、name はサーバの FormDataDecoder が読むフィールドと必ず一致します。",
      controlsH2    = "すべてのフォームコントロール",
      controlsIntro =
        "各コントロールに型チェック済みの spread ヘルパーがあり、フィールドから初期化します。form.text（input）、form.checkbox（Boolean から checked）、form.radio(_.f, option)、form.select / form.option（value + 一致時に checked/selected）。<textarea> は子コンテンツ補間で内容を入れます。すべて同じ FieldCodec を再利用します。",
      customH2    = "カスタムフィールド型",
      customIntro =
        "フィールドは FieldCodec でデコード/エンコードされます。String・Int・Long・Double・Boolean・Option・List は組み込みで、独自ドメイン型は既存コーデックを imap/eimap でマップして追加します。1 つの FieldCodec がサーバのデコードと form.text の value の両方を駆動するので、カスタム型も正しく往復します。ネストした case class は階層キー（field.subfield）でデコードされ、nameOf/text はネストしたセレクタ（form.nameOf(_.address.city)）を受け付けます。",
      csrfH2    = "CSRF 保護",
      csrfIntro =
        "CSRF フックでクロスサイトのフォーム送信からアクションを守ります。状態を変更するフォーム POST に対し、リクエストの Origin がサーバと一致することを要求し（不一致は 403）、ネイティブと use:enhance の両方の送信をカバーします。ループバックホストは http を既定とするのでローカル開発でもそのまま動きます。",
      reactivityTitle = "リアクティビティ: .value ではなく State/Signal を渡す",
      reactivityText  =
        "Melt は属性・リスト・条件を「オーバーロードで」リアクティブにします。プレーンな .value（一度きり）ではなく State/Signal を渡すと subscribe されます。よって disabled={form.submitting}（.value を付けない）とし、エラー表示は form.data を source とする条件式で駆動します。そうしないと、バリデーション Failure で状態は更新されても DOM が再描画されません。",
      progressiveTitle = "JavaScript 無しでも動く",
      progressiveText  = "ネイティブ <form> 経路が土台なので、JavaScript を無効にしてもフォームは送信・検証されます。use:enhance は純粋な拡張であり、必須ではありません。"
    ),
    serverFunctions = GuideServerFunctions(
      lead =
        "サーバー関数とは、サーバー側に一度だけ書いた関数を、あたかもローカル関数のようにコンポーネントから直接呼び出せる仕組みです。実際の処理は常にサーバーで実行されます。HTTP エンドポイントとクライアントの fetch は Melt が生成し、引数と戻り値の型を両サイドで一致させ続けます。データをブラウザとサーバーの間でやり取りするためだけにエンドポイントや fetch、JSON コーデックを手書きする必要はありません。",
      whatH2      = "サーバー関数とは？",
      whatProblem =
        "通常、ブラウザとサーバーの間でデータをやり取りするには、3 つのものを書いて手作業で整合させ続ける必要があります。サーバーの HTTP エンドポイント、クライアントの fetch 呼び出し、そしてリクエスト／レスポンスの型です。フィールド名を 1 つ変えれば 3 箇所を直すことになり、ずれても何も警告してくれません。",
      whatModel =
        "サーバー関数は、これを 1 つの宣言に畳み込みます。一度定義すれば、普通の関数のように呼び出せ、処理はサーバーで実行されます。これは古くからある RPC（リモートプロシージャコール）の考え方です。呼び出しはローカルに見えますが実際はサーバーで実行され、ネットワークの配管はフレームワークが担います（リクエストは無くなったわけではなく、関数呼び出しとして包まれているだけです）。",
      whatServerOnly =
        "本体はサーバーでしか実行されないため、データベースやシークレットを自由に使えます。それらがブラウザバンドルに混入することはありません。また契約は 1 つの共有定義なので、引数と戻り値の型が両サイドでずれることはありません。",
      whatKinds =
        "Melt には 2 種類あります。query はデータを読み取り、ローディング／エラー状態を持つリアクティブな結果を返します。command はデータを変更し、イベントハンドラから呼び出します。それ以外（seed、single-flight による再取得、楽観的更新、フィールドごとのフォームイシュー）は、すべてこの 2 つの上に成り立ちます。",
      contractH2    = "契約（コントラクト）",
      contractIntro =
        "関数は共有ファイルに定義します。サーバーとクライアントの両方でコンパイルされるので、両サイドが同じ型に合意します。ServerFn.query は読み取り、ServerFn.command は変更です。文字列はエンドポイントの論理名です。",
      queryH2    = "query — リアクティブな読み取り",
      queryIntro =
        "ServerFn.query は読み取りを定義します。呼び出すと Query が返り、その state は Signal[Async[Out]]（Loading / Done / Failed）で、match で描画します。ページローダーの prop から seed すれば、SSR がデータを描画し、クライアントはローディングのちらつきや再取得なしで hydration します。refresh() で任意に再取得できます。",
      commandH2    = "command — 変更",
      commandIntro =
        "ServerFn.command は変更を定義し、イベントハンドラから Future として呼び出します。2xx 以外の応答は Future を ServerFnException で失敗させます。",
      singleFlightH2    = "single-flight（単一往復）",
      singleFlightIntro =
        "変更は同じ往復で関連 query を更新できます。dispatch(in).updates(query).run() は変更を実行し、要求された query をサーバーで再実行して新しい値をレスポンスに相乗りさせ、クライアントは 1 往復でそれらを更新します。",
      optimisticH2    = "楽観的更新",
      optimisticIntro =
        "optimistic(query)(f) は run() 実行の瞬間に（サーバー応答を待たず）期待する変更を先に反映し、成功時は同じ往復の確定値でリコンサイル、失敗時は自動でロールバックします。",
      issuesH2    = "フィールドごとの検証イシュー",
      issuesIntro =
        "フォームモデルは errors: Map[String, List[String]] としてフィールドごとの検証イシューを保持できます。検証失敗時、アクションはこのマップを埋めたデータを返し、コンポーネントは各メッセージを対応する入力の隣に表示します（SvelteKit の fields.x.issues() 相当）。",
      invalidateH2    = "フォーム成功時に query を再取得",
      invalidateIntro =
        "Form(…).invalidates(query) でフォームアクションをリアクティブな query に接続します。フォーム送信が成功すると、宣言した各 query が再取得され、一覧が変更を反映します（SvelteKit の invalidate-on-success / TanStack の invalidateQueries 相当）。クライアントでのみ動作し、サーバーでは再取得は no-op です。",
      serverOnlyTitle = "構造的に server-only",
      serverOnlyText  =
        "app.serve に渡す実装はサーバーでしかコンパイルされません。JVM では Scala.js がコンパイルできない java.sql やシークレットを参照できるため、DB クライアントがブラウザバンドルに混入することは決してありません。lint ルールではなく構造的な保証です。",
      handlerTitle = "command はイベントハンドラ内で実行",
      handlerText  =
        "dispatch / optimistic / run はクライアント専用で、SSR 出力から除去されるイベントハンドラ内に置きます。そのため共有コンポーネントも JVM でコンパイルできます。query（seeded / refresh）と Async の match は両プラットフォームで描画されます。.run() にはスコープ内に ExecutionContext が必要です（import scala.concurrent.ExecutionContext.Implicits.global）。"
    )
  )

  def apply(lang: String): GuideI18n = if lang == "ja" then ja else en
