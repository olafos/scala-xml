package scala.xml.parsing ;

import scala.xml.dtd._ ;
import scala.util.logging.Logged ;

abstract class ValidatingMarkupHandler extends MarkupHandler with Logged {

  var rootLabel:String = _;
  var qStack: List[Int] = Nil;
  var qCurrent: Int = -1;

  var declStack: List[ElemDecl] = Nil;
  var declCurrent: ElemDecl = null;

  final override val isValidating = true;

  /*
  override def checkChildren(pos:int, pre: String, label:String,ns:NodeSeq): Unit = {
    Console.println("checkChildren()");
    val decl = lookupElemDecl(label);
    // @todo: nice error message
    val res = decl.contentModel.validate(ns);
    Console.println("res = "+res);
    if(!res)
      error("invalid!");
  }
  */

  override def endDTD(n:String) = {
    rootLabel = n;
  }
  override def elemStart(pos: int, pre: String, label: String, attrs: MetaData, scope:NamespaceBinding): Unit = {

    def advanceDFA(dm:DFAContentModel) = {
      val trans = dm.dfa.delta(qCurrent);
      log("advanceDFA(dm): "+dm);
      log("advanceDFA(trans): "+trans);
      trans.get(ContentModel.ElemName(label)).match {
          case Some(qNew) => qCurrent = qNew
          case _          => reportValidationError(pos, "DTD says, wrong element, expected one of "+trans.keys.toString);
        }
    }
    // advance in current automaton
    log("[qCurrent = "+qCurrent+" visiting "+label+"]");

    if(qCurrent == -1) { // root
      log("  checking root");
      if(label != rootLabel)
        reportValidationError(pos, "this element should be "+rootLabel);
    } else {
      log("  checking node");
      declCurrent.contentModel.match {
        case ANY         =>

          case EMPTY       =>
            reportValidationError(pos, "DTD says, no elems, no text allowed here");
        case PCDATA      =>
          reportValidationError(pos, "DTD says, no elements allowed here");

        case m@MIXED(r)    => advanceDFA(m);
        case e@ELEMENTS(r) => advanceDFA(e);
      }
    }
    // push state, decl
    qStack    =    qCurrent :: qStack;
    declStack = declCurrent :: declStack;

    declCurrent = lookupElemDecl(label);
    qCurrent = 0;
    log("  done  now");
  }

  override def elemEnd(pos: int, pre: String, label: String): Unit = {
    log("  elemEnd");
    qCurrent = qStack.head;
    qStack   = qStack.tail;
    declCurrent = declStack.head;
    declStack   = declStack.tail;
    log("    qCurrent now"+qCurrent);
    log("    declCurrent now"+declCurrent);
  }

  final override def elemDecl(name: String, cmstr: String): Unit =
    decls = ElemDecl( name,  ContentModel.parse(cmstr)) :: decls;

  final override def attListDecl(name: String, attList: List[AttrDecl]): Unit =
    decls = AttListDecl( name, attList) :: decls;

  final override def unparsedEntityDecl(name: String, extID: ExternalID, notat: String): Unit = {
    decls =  UnparsedEntityDecl( name, extID, notat) :: decls;
  }

  final override def notationDecl(notat: String, extID: ExternalID): Unit =
    decls = NotationDecl( notat, extID) :: decls;

  final override def peReference(name: String): Unit =
    decls = PEReference( name ) :: decls;

  /** report a syntax error */
  def reportValidationError(pos: Int, str: String): Unit;

}