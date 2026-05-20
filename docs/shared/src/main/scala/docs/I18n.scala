/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

object I18n:

  case class Nav(guide: String, api: String, examples: String, changelog: String)

  case class SidebarSections(intro: String, basics: String, advanced: String, server: String)

  case class SidebarLinks(links: Map[String, String]):
    def apply(slug: String): String = links.getOrElse(slug, slug)

  case class HomeFeature(num: String, title: String, body: String)

  case class Home(
    tagline:        String,
    cta1:           String,
    cta2:           String,
    meta:           List[String],
    featuresLabel:  String,
    featuresTitle:  String,
    featuresSub:    String,
    features:       List[HomeFeature],
    demoLabel:      String,
    demoTitle:      String,
    demoSub:        String,
    demoCardLabel:  String,
    pageNavNextDir: String,
    pageNavNext:    String
  )

  case class Dict(
    nav:             Nav,
    sidebarSections: SidebarSections,
    guideLinks:      SidebarLinks,
    apiLinks:        SidebarLinks,
    exampleLinks:    SidebarLinks,
    home:            Home
  )

  val en: Dict = Dict(
    nav = Nav(
      guide     = "Guide",
      api       = "API Reference",
      examples  = "Examples",
      changelog = "Changelog"
    ),
    sidebarSections = SidebarSections(
      intro    = "Getting Started",
      basics   = "Basics",
      advanced = "Advanced",
      server   = "Server"
    ),
    guideLinks = SidebarLinks(
      Map(
        "introduction"     -> "What is Melt",
        "installation"     -> "Installation",
        "quick-start"      -> "Quick Start",
        "components"       -> "Components",
        "template-syntax"  -> "Template Syntax",
        "reactivity"       -> "Reactivity",
        "computed"         -> "Computed",
        "effects"          -> "Effects",
        "events"           -> "Events",
        "lifecycle"        -> "Lifecycle",
        "control-flow"     -> "Control Flow",
        "special-elements" -> "Special Elements",
        "transitions"      -> "Transitions",
        "trusted-html"     -> "Trusted HTML",
        "css"              -> "CSS",
        "testing"          -> "Testing",
        "routing"          -> "Routing",
        "ssr"              -> "SSR",
        "ssg"              -> "SSG",
        "adapters"         -> "Adapters"
      )
    ),
    apiLinks = SidebarLinks(
      Map(
        "template-syntax" -> "Template Syntax",
        "runtime"         -> "Runtime",
        "meltkit"         -> "Meltkit",
        "meltkit-ssg"     -> "Meltkit SSG",
        "compiler"        -> "Compiler",
        "sbt-plugin"      -> "sbt Plugin"
      )
    ),
    exampleLinks = SidebarLinks(
      Map(
        "counter"  -> "Counter",
        "todo-app" -> "Todo App"
      )
    ),
    home = Home(
      tagline =
        "A single-file component framework for Scala.js. Compile Scala, HTML and CSS to lean DOM code — no runtime framework.",
      cta1          = "Get Started",
      cta2          = "View on GitHub",
      meta          = List("v0.1.0-SNAPSHOT", "Apache 2.0", "Scala 3.3.7"),
      featuresLabel = "Why Melt",
      featuresTitle = "The compiler is the framework.",
      featuresSub   =
        "Inspired by Svelte. Powered by the Scala type system. Targeting browsers, servers and statically-generated sites from the same .melt source.",
      features = List(
        HomeFeature(
          "01",
          "Single-file components",
          "Write Scala, HTML and scoped CSS in one .melt file. The compiler emits direct DOM code — no virtual DOM, no runtime overhead."
        ),
        HomeFeature(
          "02",
          "Fully typed templates",
          "Every expression in your template is checked by scalac. Refactors propagate; typos fail at compile time, not on a Tuesday in production."
        ),
        HomeFeature(
          "03",
          "Tiny reactive runtime",
          "Just Var, Signal and Bind. Fine-grained updates, no re-rendering trees, no diffing. Effects and memos compose naturally."
        ),
        HomeFeature(
          "04",
          "SPA · SSR · SSG, one source",
          "The same component renders on the JVM as an HTML string or in the browser as live DOM. Hydration is opt-in."
        )
      ),
      demoLabel      = "Try it",
      demoTitle      = "A reactive counter, in fifteen lines.",
      demoSub        = "The .melt file on the left compiles to the live component on the right. Click the buttons.",
      demoCardLabel  = "// live preview",
      pageNavNextDir = "Next",
      pageNavNext    = "Quick Start"
    )
  )

  val ja: Dict = Dict(
    nav = Nav(
      guide     = "ガイド",
      api       = "API リファレンス",
      examples  = "サンプル",
      changelog = "変更履歴"
    ),
    sidebarSections = SidebarSections(
      intro    = "はじめに",
      basics   = "基本",
      advanced = "応用",
      server   = "サーバー"
    ),
    guideLinks = SidebarLinks(
      Map(
        "introduction"     -> "Melt とは",
        "installation"     -> "インストール",
        "quick-start"      -> "クイックスタート",
        "components"       -> "コンポーネント",
        "template-syntax"  -> "テンプレート構文",
        "reactivity"       -> "リアクティビティ",
        "computed"         -> "コンピューテッド",
        "effects"          -> "エフェクト",
        "events"           -> "イベント",
        "lifecycle"        -> "ライフサイクル",
        "control-flow"     -> "制御フロー",
        "special-elements" -> "特殊要素",
        "transitions"      -> "トランジション",
        "trusted-html"     -> "Trusted HTML",
        "css"              -> "CSS",
        "testing"          -> "テスト",
        "routing"          -> "ルーティング",
        "ssr"              -> "SSR",
        "ssg"              -> "SSG",
        "adapters"         -> "アダプター"
      )
    ),
    apiLinks = SidebarLinks(
      Map(
        "template-syntax" -> "テンプレート構文",
        "runtime"         -> "ランタイム",
        "meltkit"         -> "Meltkit",
        "meltkit-ssg"     -> "Meltkit SSG",
        "compiler"        -> "コンパイラ",
        "sbt-plugin"      -> "sbt プラグイン"
      )
    ),
    exampleLinks = SidebarLinks(
      Map(
        "counter"  -> "カウンター",
        "todo-app" -> "Todo アプリ"
      )
    ),
    home = Home(
      tagline       = "Scala.js 向けのシングルファイルコンポーネントフレームワーク。Scala・HTML・CSS を 1 ファイルで書き、コンパイラが素の DOM コードを生成します。",
      cta1          = "はじめる",
      cta2          = "GitHub で見る",
      meta          = List("v0.1.0-SNAPSHOT", "Apache 2.0", "Scala 3.3.7"),
      featuresLabel = "Melt を選ぶ理由",
      featuresTitle = "コンパイラがフレームワーク。",
      featuresSub   = "Svelte にインスパイアされ、Scala の型システムに支えられた SFC。同じ .melt ファイルからブラウザ・サーバー・静的サイトを出力します。",
      features      = List(
        HomeFeature(
          "01",
          "シングルファイル構成",
          "Scala・HTML・スコープ付き CSS を 1 つの .melt ファイルに記述。コンパイラが直接 DOM コードを生成し、仮想 DOM もランタイムオーバーヘッドもありません。"
        ),
        HomeFeature("02", "完全に型付けされたテンプレート", "テンプレート内の式まで scalac が型チェック。リファクタリングは安全に伝播し、typo は本番ではなくコンパイル時に失敗します。"),
        HomeFeature("03", "最小のリアクティブランタイム", "Var / Signal / Bind の 3 つだけ。木の再描画も diff もない、細粒度の更新。エフェクトとメモが自然に合成できます。"),
        HomeFeature("04", "SPA・SSR・SSG をひとつのソースで", "同じコンポーネントが JVM では HTML 文字列、ブラウザでは生きた DOM になります。ハイドレーションはオプトイン。")
      ),
      demoLabel      = "試してみる",
      demoTitle      = "リアクティブカウンター、わずか 15 行。",
      demoSub        = "左の .melt ファイルが、右のライブコンポーネントへとコンパイルされます。",
      demoCardLabel  = "// ライブプレビュー",
      pageNavNextDir = "次へ",
      pageNavNext    = "クイックスタート"
    )
  )

  def apply(lang: String): Dict = if lang == "ja" then ja else en
