import org.scalajs.dom
import components.App

object Main:
  def main(args: Array[String]): Unit =
    App.mount(dom.document.getElementById("app"))
