// Generated from /Users/bclement/git/klang2/src/grammar/Model.g4 by ANTLR 4.7
package k.frontend;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class ModelParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, T__30=31, 
		T__31=32, T__32=33, T__33=34, T__34=35, T__35=36, T__36=37, T__37=38, 
		T__38=39, T__39=40, T__40=41, T__41=42, T__42=43, T__43=44, T__44=45, 
		T__45=46, T__46=47, T__47=48, T__48=49, T__49=50, T__50=51, T__51=52, 
		T__52=53, T__53=54, T__54=55, T__55=56, T__56=57, T__57=58, T__58=59, 
		T__59=60, T__60=61, T__61=62, T__62=63, T__63=64, T__64=65, T__65=66, 
		T__66=67, T__67=68, T__68=69, T__69=70, T__70=71, T__71=72, T__72=73, 
		T__73=74, T__74=75, T__75=76, T__76=77, T__77=78, T__78=79, T__79=80, 
		T__80=81, T__81=82, T__82=83, T__83=84, T__84=85, T__85=86, T__86=87, 
		T__87=88, T__88=89, T__89=90, T__90=91, T__91=92, T__92=93, T__93=94, 
		T__94=95, T__95=96, T__96=97, T__97=98, T__98=99, T__99=100, T__100=101, 
		T__101=102, T__102=103, T__103=104, T__104=105, T__105=106, T__106=107, 
		T__107=108, T__108=109, T__109=110, T__110=111, T__111=112, T__112=113, 
		T__113=114, T__114=115, T__115=116, T__116=117, T__117=118, T__118=119, 
		T__119=120, T__120=121, T__121=122, T__122=123, T__123=124, Keyword=125, 
		SUCHTHAT=126, DateLiteral=127, DurationLiteral=128, IntegerLiteral=129, 
		RealLiteral=130, BooleanLiteral=131, NullLiteral=132, ThisLiteral=133, 
		CharacterLiteral=134, StringLiteral=135, Identifier=136, CommentBorder=137, 
		CBlockComment=138, BlockComment=139, CLineComment=140, LineComment=141, 
		WS=142, SEP=143;
	public static final int
		RULE_model = 0, RULE_modelThings = 1, RULE_modelThing = 2, RULE_packageDeclaration = 3, 
		RULE_importDeclaration = 4, RULE_importLanguage = 5, RULE_annotationDeclaration = 6, 
		RULE_annotation = 7, RULE_topDeclaration = 8, RULE_entityDeclaration = 9, 
		RULE_typeParameters = 10, RULE_typeParameter = 11, RULE_typeBound = 12, 
		RULE_extending = 13, RULE_block = 14, RULE_blockDeclaration = 15, RULE_memberDeclaration = 16, 
		RULE_shareDeclaration = 17, RULE_renameDeclaration = 18, RULE_shadowDeclaration = 19, 
		RULE_optimizeDeclaration = 20, RULE_typeDeclaration = 21, RULE_propertyDeclaration = 22, 
		RULE_propertyModifier = 23, RULE_functionDeclaration = 24, RULE_paramList = 25, 
		RULE_param = 26, RULE_functionSpecification = 27, RULE_constraint = 28, 
		RULE_multiplicity = 29, RULE_expressionOrStar = 30, RULE_type = 31, RULE_primitiveType = 32, 
		RULE_classIdentifier = 33, RULE_collectionKind = 34, RULE_typeArguments = 35, 
		RULE_expression = 36, RULE_match = 37, RULE_argumentList = 38, RULE_positionalArgumentList = 39, 
		RULE_namedArgumentList = 40, RULE_namedArgument = 41, RULE_collectionOrType = 42, 
		RULE_rngBindingList = 43, RULE_rngBinding = 44, RULE_patternList = 45, 
		RULE_pattern = 46, RULE_identifierList = 47, RULE_expressionList = 48, 
		RULE_qualifiedName = 49, RULE_literal = 50;
	public static final String[] ruleNames = {
		"model", "modelThings", "modelThing", "packageDeclaration", "importDeclaration", 
		"importLanguage", "annotationDeclaration", "annotation", "topDeclaration", 
		"entityDeclaration", "typeParameters", "typeParameter", "typeBound", "extending", 
		"block", "blockDeclaration", "memberDeclaration", "shareDeclaration", 
		"renameDeclaration", "shadowDeclaration", "optimizeDeclaration", "typeDeclaration", 
		"propertyDeclaration", "propertyModifier", "functionDeclaration", "paramList", 
		"param", "functionSpecification", "constraint", "multiplicity", "expressionOrStar", 
		"type", "primitiveType", "classIdentifier", "collectionKind", "typeArguments", 
		"expression", "match", "argumentList", "positionalArgumentList", "namedArgumentList", 
		"namedArgument", "collectionOrType", "rngBindingList", "rngBinding", "patternList", 
		"pattern", "identifierList", "expressionList", "qualifiedName", "literal"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'package'", "'{'", "'}'", "'import'", "'.'", "'*'", "'java'", "'python'", 
		"'annotation'", "':'", "'@'", "'('", "')'", "'class'", "'assoc'", "'['", 
		"','", "']'", "'+'", "'extends'", "'share'", "'rename'", "'::'", "'as'", 
		"'shadow'", "'minimize'", "'maximize'", "'weight'", "'type'", "'='", "':='", 
		"'part'", "'var'", "'val'", "'ordered'", "'unique'", "'source'", "'target'", 
		"'fun'", "'pre'", "'post'", "'req'", "'soft'", "'->'", "'{|'", "'|}'", 
		"'Bool'", "'Char'", "'Int'", "'Real'", "'String'", "'Unit'", "'Time'", 
		"'Duration'", "'BitVec'", "'Float32'", "'Float64'", "'Int8'", "'Int16'", 
		"'Int32'", "'Int64'", "'UInt8'", "'UInt16'", "'UInt32'", "'UInt64'", "'Class'", 
		"'Set'", "'OSet'", "'Bag'", "'Seq'", "'Tuple'", "'new'", "'!'", "'if'", 
		"'then'", "'else'", "'match'", "'with'", "'while'", "'do'", "'for'", "'in'", 
		"'..'", "'|'", "'is'", "'/'", "'%'", "'inter'", "'\\'", "'++'", "'#'", 
		"'^'", "'-'", "'union'", "'band'", "'bor'", "'bxor'", "'shl'", "'shr'", 
		"'sar'", "'<='", "'>='", "'<'", "'>'", "'!='", "'isin'", "'!isin'", "'subset'", 
		"'psubset'", "'&&'", "'||'", "'=>'", "'<=>'", "'assert'", "'bnot'", "'~'", 
		"'forall'", "'exists'", "'continue'", "'break'", "'return'", "'$result'", 
		"'case'", "'_'", null, "':-'", null, null, null, null, null, "'null'", 
		"'this'", null, null, null, null, null, null, null, null, null, "';'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, "Keyword", "SUCHTHAT", "DateLiteral", "DurationLiteral", 
		"IntegerLiteral", "RealLiteral", "BooleanLiteral", "NullLiteral", "ThisLiteral", 
		"CharacterLiteral", "StringLiteral", "Identifier", "CommentBorder", "CBlockComment", 
		"BlockComment", "CLineComment", "LineComment", "WS", "SEP"
	};
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "Model.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public ModelParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ModelContext extends ParserRuleContext {
		public ModelThingsContext modelThings() {
			return getRuleContext(ModelThingsContext.class,0);
		}
		public TerminalNode EOF() { return getToken(ModelParser.EOF, 0); }
		public ModelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_model; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitModel(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModelContext model() throws RecognitionException {
		ModelContext _localctx = new ModelContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_model);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(102);
			modelThings();
			setState(103);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModelThingsContext extends ParserRuleContext {
		public List<ModelThingContext> modelThing() {
			return getRuleContexts(ModelThingContext.class);
		}
		public ModelThingContext modelThing(int i) {
			return getRuleContext(ModelThingContext.class,i);
		}
		public ModelThingsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modelThings; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitModelThings(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModelThingsContext modelThings() throws RecognitionException {
		ModelThingsContext _localctx = new ModelThingsContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_modelThings);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(108);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(105);
					modelThing();
					}
					} 
				}
				setState(110);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModelThingContext extends ParserRuleContext {
		public PackageDeclarationContext packageDeclaration() {
			return getRuleContext(PackageDeclarationContext.class,0);
		}
		public ImportDeclarationContext importDeclaration() {
			return getRuleContext(ImportDeclarationContext.class,0);
		}
		public AnnotationDeclarationContext annotationDeclaration() {
			return getRuleContext(AnnotationDeclarationContext.class,0);
		}
		public TopDeclarationContext topDeclaration() {
			return getRuleContext(TopDeclarationContext.class,0);
		}
		public ModelThingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modelThing; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitModelThing(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ModelThingContext modelThing() throws RecognitionException {
		ModelThingContext _localctx = new ModelThingContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_modelThing);
		try {
			setState(115);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__0:
				enterOuterAlt(_localctx, 1);
				{
				setState(111);
				packageDeclaration();
				}
				break;
			case T__3:
				enterOuterAlt(_localctx, 2);
				{
				setState(112);
				importDeclaration();
				}
				break;
			case T__8:
				enterOuterAlt(_localctx, 3);
				{
				setState(113);
				annotationDeclaration();
				}
				break;
			case T__1:
			case T__6:
			case T__7:
			case T__10:
			case T__11:
			case T__13:
			case T__14:
			case T__20:
			case T__21:
			case T__24:
			case T__25:
			case T__26:
			case T__28:
			case T__31:
			case T__32:
			case T__33:
			case T__34:
			case T__35:
			case T__36:
			case T__37:
			case T__38:
			case T__41:
			case T__42:
			case T__44:
			case T__46:
			case T__47:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case T__60:
			case T__61:
			case T__62:
			case T__63:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__76:
			case T__78:
			case T__80:
			case T__92:
			case T__113:
			case T__114:
			case T__116:
			case T__117:
			case T__118:
			case T__119:
			case T__120:
			case T__121:
			case T__123:
			case DateLiteral:
			case DurationLiteral:
			case IntegerLiteral:
			case RealLiteral:
			case BooleanLiteral:
			case NullLiteral:
			case ThisLiteral:
			case CharacterLiteral:
			case StringLiteral:
			case Identifier:
				enterOuterAlt(_localctx, 4);
				{
				setState(114);
				topDeclaration();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PackageDeclarationContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public ModelThingsContext modelThings() {
			return getRuleContext(ModelThingsContext.class,0);
		}
		public PackageDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_packageDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPackageDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PackageDeclarationContext packageDeclaration() throws RecognitionException {
		PackageDeclarationContext _localctx = new PackageDeclarationContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_packageDeclaration);
		try {
			setState(127);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(117);
				match(T__0);
				setState(118);
				qualifiedName();
				setState(119);
				match(T__1);
				setState(120);
				modelThings();
				setState(121);
				match(T__2);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(123);
				match(T__0);
				setState(124);
				qualifiedName();
				setState(125);
				modelThings();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportDeclarationContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public ImportLanguageContext importLanguage() {
			return getRuleContext(ImportLanguageContext.class,0);
		}
		public ImportDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitImportDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportDeclarationContext importDeclaration() throws RecognitionException {
		ImportDeclarationContext _localctx = new ImportDeclarationContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_importDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(129);
			match(T__3);
			setState(131);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__6 || _la==T__7) {
				{
				setState(130);
				importLanguage();
				}
			}

			setState(133);
			qualifiedName();
			setState(136);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__4) {
				{
				setState(134);
				match(T__4);
				setState(135);
				match(T__5);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ImportLanguageContext extends ParserRuleContext {
		public ImportLanguageContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_importLanguage; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitImportLanguage(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ImportLanguageContext importLanguage() throws RecognitionException {
		ImportLanguageContext _localctx = new ImportLanguageContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_importLanguage);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(138);
			_la = _input.LA(1);
			if ( !(_la==T__6 || _la==T__7) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationDeclarationContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public AnnotationDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotationDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAnnotationDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AnnotationDeclarationContext annotationDeclaration() throws RecognitionException {
		AnnotationDeclarationContext _localctx = new AnnotationDeclarationContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_annotationDeclaration);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(140);
			match(T__8);
			setState(141);
			match(Identifier);
			setState(142);
			match(T__9);
			setState(143);
			type(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AnnotationContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AnnotationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_annotation; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAnnotation(this);
			else return visitor.visitChildren(this);
		}
	}

	public final AnnotationContext annotation() throws RecognitionException {
		AnnotationContext _localctx = new AnnotationContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_annotation);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(145);
			match(T__10);
			setState(146);
			match(Identifier);
			setState(151);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				{
				setState(147);
				match(T__11);
				setState(148);
				expression(0);
				setState(149);
				match(T__12);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TopDeclarationContext extends ParserRuleContext {
		public EntityDeclarationContext entityDeclaration() {
			return getRuleContext(EntityDeclarationContext.class,0);
		}
		public List<AnnotationContext> annotation() {
			return getRuleContexts(AnnotationContext.class);
		}
		public AnnotationContext annotation(int i) {
			return getRuleContext(AnnotationContext.class,i);
		}
		public MemberDeclarationContext memberDeclaration() {
			return getRuleContext(MemberDeclarationContext.class,0);
		}
		public TopDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_topDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTopDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TopDeclarationContext topDeclaration() throws RecognitionException {
		TopDeclarationContext _localctx = new TopDeclarationContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_topDeclaration);
		int _la;
		try {
			setState(167);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(156);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__10) {
					{
					{
					setState(153);
					annotation();
					}
					}
					setState(158);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(159);
				entityDeclaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(163);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__10) {
					{
					{
					setState(160);
					annotation();
					}
					}
					setState(165);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(166);
				memberDeclaration();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class EntityDeclarationContext extends ParserRuleContext {
		public List<TerminalNode> Identifier() { return getTokens(ModelParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(ModelParser.Identifier, i);
		}
		public TerminalNode Keyword() { return getToken(ModelParser.Keyword, 0); }
		public TypeParametersContext typeParameters() {
			return getRuleContext(TypeParametersContext.class,0);
		}
		public ExtendingContext extending() {
			return getRuleContext(ExtendingContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public EntityDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_entityDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitEntityDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final EntityDeclarationContext entityDeclaration() throws RecognitionException {
		EntityDeclarationContext _localctx = new EntityDeclarationContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_entityDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(169);
			_la = _input.LA(1);
			if ( !(_la==T__13 || _la==T__14 || _la==Identifier) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(171);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==Keyword) {
				{
				setState(170);
				match(Keyword);
				}
			}

			setState(173);
			match(Identifier);
			setState(175);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__15) {
				{
				setState(174);
				typeParameters();
				}
			}

			setState(178);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__19) {
				{
				setState(177);
				extending();
				}
			}

			setState(184);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
			case 1:
				{
				setState(180);
				match(T__1);
				setState(181);
				block();
				setState(182);
				match(T__2);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeParametersContext extends ParserRuleContext {
		public List<TypeParameterContext> typeParameter() {
			return getRuleContexts(TypeParameterContext.class);
		}
		public TypeParameterContext typeParameter(int i) {
			return getRuleContext(TypeParameterContext.class,i);
		}
		public TypeParametersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeParameters; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeParameters(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeParametersContext typeParameters() throws RecognitionException {
		TypeParametersContext _localctx = new TypeParametersContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_typeParameters);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(186);
			match(T__15);
			setState(187);
			typeParameter();
			setState(192);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(188);
				match(T__16);
				setState(189);
				typeParameter();
				}
				}
				setState(194);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(195);
			match(T__17);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeParameterContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeBoundContext typeBound() {
			return getRuleContext(TypeBoundContext.class,0);
		}
		public TypeParameterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeParameter; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeParameter(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeParameterContext typeParameter() throws RecognitionException {
		TypeParameterContext _localctx = new TypeParameterContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_typeParameter);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(197);
			match(Identifier);
			setState(200);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__9) {
				{
				setState(198);
				match(T__9);
				setState(199);
				typeBound();
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeBoundContext extends ParserRuleContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public TypeBoundContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeBound; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeBound(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeBoundContext typeBound() throws RecognitionException {
		TypeBoundContext _localctx = new TypeBoundContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_typeBound);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(202);
			type(0);
			setState(207);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__18) {
				{
				{
				setState(203);
				match(T__18);
				setState(204);
				type(0);
				}
				}
				setState(209);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExtendingContext extends ParserRuleContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public ExtendingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_extending; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitExtending(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExtendingContext extending() throws RecognitionException {
		ExtendingContext _localctx = new ExtendingContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_extending);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(210);
			match(T__19);
			setState(211);
			type(0);
			setState(216);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(212);
				match(T__16);
				setState(213);
				type(0);
				}
				}
				setState(218);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BlockContext extends ParserRuleContext {
		public List<BlockDeclarationContext> blockDeclaration() {
			return getRuleContexts(BlockDeclarationContext.class);
		}
		public BlockDeclarationContext blockDeclaration(int i) {
			return getRuleContext(BlockDeclarationContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBlock(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(222);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__10) | (1L << T__11) | (1L << T__13) | (1L << T__14) | (1L << T__20) | (1L << T__21) | (1L << T__24) | (1L << T__25) | (1L << T__26) | (1L << T__28) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38) | (1L << T__41) | (1L << T__42) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
				{
				{
				setState(219);
				blockDeclaration();
				}
				}
				setState(224);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BlockDeclarationContext extends ParserRuleContext {
		public MemberDeclarationContext memberDeclaration() {
			return getRuleContext(MemberDeclarationContext.class,0);
		}
		public List<AnnotationContext> annotation() {
			return getRuleContexts(AnnotationContext.class);
		}
		public AnnotationContext annotation(int i) {
			return getRuleContext(AnnotationContext.class,i);
		}
		public BlockDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blockDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBlockDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final BlockDeclarationContext blockDeclaration() throws RecognitionException {
		BlockDeclarationContext _localctx = new BlockDeclarationContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_blockDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(228);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__10) {
				{
				{
				setState(225);
				annotation();
				}
				}
				setState(230);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(231);
			memberDeclaration();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MemberDeclarationContext extends ParserRuleContext {
		public TypeDeclarationContext typeDeclaration() {
			return getRuleContext(TypeDeclarationContext.class,0);
		}
		public EntityDeclarationContext entityDeclaration() {
			return getRuleContext(EntityDeclarationContext.class,0);
		}
		public PropertyDeclarationContext propertyDeclaration() {
			return getRuleContext(PropertyDeclarationContext.class,0);
		}
		public FunctionDeclarationContext functionDeclaration() {
			return getRuleContext(FunctionDeclarationContext.class,0);
		}
		public ConstraintContext constraint() {
			return getRuleContext(ConstraintContext.class,0);
		}
		public OptimizeDeclarationContext optimizeDeclaration() {
			return getRuleContext(OptimizeDeclarationContext.class,0);
		}
		public ShareDeclarationContext shareDeclaration() {
			return getRuleContext(ShareDeclarationContext.class,0);
		}
		public RenameDeclarationContext renameDeclaration() {
			return getRuleContext(RenameDeclarationContext.class,0);
		}
		public ShadowDeclarationContext shadowDeclaration() {
			return getRuleContext(ShadowDeclarationContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MemberDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_memberDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitMemberDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MemberDeclarationContext memberDeclaration() throws RecognitionException {
		MemberDeclarationContext _localctx = new MemberDeclarationContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_memberDeclaration);
		try {
			setState(243);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(233);
				typeDeclaration();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(234);
				entityDeclaration();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(235);
				propertyDeclaration();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(236);
				functionDeclaration();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(237);
				constraint();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(238);
				optimizeDeclaration();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(239);
				shareDeclaration();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(240);
				renameDeclaration();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(241);
				shadowDeclaration();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(242);
				expression(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ShareDeclarationContext extends ParserRuleContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public ShareDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shareDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitShareDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ShareDeclarationContext shareDeclaration() throws RecognitionException {
		ShareDeclarationContext _localctx = new ShareDeclarationContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_shareDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(245);
			match(T__20);
			setState(246);
			type(0);
			setState(251);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(247);
				match(T__16);
				setState(248);
				type(0);
				}
				}
				setState(253);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(255);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEP) {
				{
				setState(254);
				match(SEP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RenameDeclarationContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public List<TerminalNode> Identifier() { return getTokens(ModelParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(ModelParser.Identifier, i);
		}
		public RenameDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_renameDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitRenameDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RenameDeclarationContext renameDeclaration() throws RecognitionException {
		RenameDeclarationContext _localctx = new RenameDeclarationContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_renameDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(257);
			match(T__21);
			setState(258);
			qualifiedName();
			setState(259);
			match(T__22);
			setState(260);
			match(Identifier);
			setState(261);
			match(T__23);
			setState(262);
			match(Identifier);
			setState(264);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEP) {
				{
				setState(263);
				match(SEP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ShadowDeclarationContext extends ParserRuleContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public ShadowDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_shadowDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitShadowDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ShadowDeclarationContext shadowDeclaration() throws RecognitionException {
		ShadowDeclarationContext _localctx = new ShadowDeclarationContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_shadowDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(266);
			match(T__24);
			setState(267);
			type(0);
			setState(268);
			match(Identifier);
			setState(270);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==SEP) {
				{
				setState(269);
				match(SEP);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class OptimizeDeclarationContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode IntegerLiteral() { return getToken(ModelParser.IntegerLiteral, 0); }
		public OptimizeDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_optimizeDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitOptimizeDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final OptimizeDeclarationContext optimizeDeclaration() throws RecognitionException {
		OptimizeDeclarationContext _localctx = new OptimizeDeclarationContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_optimizeDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(272);
			_la = _input.LA(1);
			if ( !(_la==T__25 || _la==T__26) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(273);
			expression(0);
			setState(276);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__27) {
				{
				setState(274);
				match(T__27);
				setState(275);
				match(IntegerLiteral);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeDeclarationContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TypeParametersContext typeParameters() {
			return getRuleContext(TypeParametersContext.class,0);
		}
		public TypeDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeDeclarationContext typeDeclaration() throws RecognitionException {
		TypeDeclarationContext _localctx = new TypeDeclarationContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_typeDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(278);
			match(T__28);
			setState(279);
			match(Identifier);
			setState(285);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__15 || _la==T__29) {
				{
				setState(281);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__15) {
					{
					setState(280);
					typeParameters();
					}
				}

				setState(283);
				match(T__29);
				setState(284);
				type(0);
				}
			}

			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PropertyDeclarationContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public List<PropertyModifierContext> propertyModifier() {
			return getRuleContexts(PropertyModifierContext.class);
		}
		public PropertyModifierContext propertyModifier(int i) {
			return getRuleContext(PropertyModifierContext.class,i);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public MultiplicityContext multiplicity() {
			return getRuleContext(MultiplicityContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PropertyDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPropertyDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyDeclarationContext propertyDeclaration() throws RecognitionException {
		PropertyDeclarationContext _localctx = new PropertyDeclarationContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_propertyDeclaration);
		int _la;
		try {
			setState(332);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,36,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(288); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(287);
					propertyModifier();
					}
					}
					setState(290); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37))) != 0) );
				setState(292);
				match(Identifier);
				setState(295);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__9) {
					{
					setState(293);
					match(T__9);
					setState(294);
					type(0);
					}
				}

				setState(298);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__15) {
					{
					setState(297);
					multiplicity();
					}
				}

				setState(302);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__29 || _la==T__30) {
					{
					setState(300);
					_la = _input.LA(1);
					if ( !(_la==T__29 || _la==T__30) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(301);
					expression(0);
					}
				}

				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(307);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37))) != 0)) {
					{
					{
					setState(304);
					propertyModifier();
					}
					}
					setState(309);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(310);
				match(Identifier);
				setState(311);
				match(T__9);
				setState(312);
				type(0);
				setState(314);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__15) {
					{
					setState(313);
					multiplicity();
					}
				}

				setState(318);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__29 || _la==T__30) {
					{
					setState(316);
					_la = _input.LA(1);
					if ( !(_la==T__29 || _la==T__30) ) {
					_errHandler.recoverInline(this);
					}
					else {
						if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
						_errHandler.reportMatch(this);
						consume();
					}
					setState(317);
					expression(0);
					}
				}

				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(323);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37))) != 0)) {
					{
					{
					setState(320);
					propertyModifier();
					}
					}
					setState(325);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(326);
				match(Identifier);
				setState(328);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==T__15) {
					{
					setState(327);
					multiplicity();
					}
				}

				setState(330);
				_la = _input.LA(1);
				if ( !(_la==T__29 || _la==T__30) ) {
				_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(331);
				expression(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PropertyModifierContext extends ParserRuleContext {
		public PropertyModifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_propertyModifier; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPropertyModifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PropertyModifierContext propertyModifier() throws RecognitionException {
		PropertyModifierContext _localctx = new PropertyModifierContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_propertyModifier);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionDeclarationContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeParametersContext typeParameters() {
			return getRuleContext(TypeParametersContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public List<FunctionSpecificationContext> functionSpecification() {
			return getRuleContexts(FunctionSpecificationContext.class);
		}
		public FunctionSpecificationContext functionSpecification(int i) {
			return getRuleContext(FunctionSpecificationContext.class,i);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ParamListContext paramList() {
			return getRuleContext(ParamListContext.class,0);
		}
		public FunctionDeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionDeclaration; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitFunctionDeclaration(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionDeclarationContext functionDeclaration() throws RecognitionException {
		FunctionDeclarationContext _localctx = new FunctionDeclarationContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_functionDeclaration);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(336);
			match(T__38);
			setState(337);
			match(Identifier);
			setState(339);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__15) {
				{
				setState(338);
				typeParameters();
				}
			}

			setState(346);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,39,_ctx) ) {
			case 1:
				{
				setState(341);
				match(T__11);
				setState(343);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==Identifier) {
					{
					setState(342);
					paramList();
					}
				}

				setState(345);
				match(T__12);
				}
				break;
			}
			setState(350);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__9) {
				{
				setState(348);
				match(T__9);
				setState(349);
				type(0);
				}
			}

			setState(355);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__39 || _la==T__40) {
				{
				{
				setState(352);
				functionSpecification();
				}
				}
				setState(357);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(362);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,42,_ctx) ) {
			case 1:
				{
				setState(358);
				match(T__1);
				setState(359);
				block();
				setState(360);
				match(T__2);
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParamListContext extends ParserRuleContext {
		public List<ParamContext> param() {
			return getRuleContexts(ParamContext.class);
		}
		public ParamContext param(int i) {
			return getRuleContext(ParamContext.class,i);
		}
		public ParamListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_paramList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitParamList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParamListContext paramList() throws RecognitionException {
		ParamListContext _localctx = new ParamListContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_paramList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(364);
			param();
			setState(369);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(365);
				match(T__16);
				setState(366);
				param();
				}
				}
				setState(371);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ParamContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ParamContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_param; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitParam(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ParamContext param() throws RecognitionException {
		ParamContext _localctx = new ParamContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(372);
			match(Identifier);
			setState(373);
			match(T__9);
			setState(374);
			type(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctionSpecificationContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public FunctionSpecificationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functionSpecification; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitFunctionSpecification(this);
			else return visitor.visitChildren(this);
		}
	}

	public final FunctionSpecificationContext functionSpecification() throws RecognitionException {
		FunctionSpecificationContext _localctx = new FunctionSpecificationContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_functionSpecification);
		try {
			setState(380);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__39:
				enterOuterAlt(_localctx, 1);
				{
				setState(376);
				match(T__39);
				setState(377);
				expression(0);
				}
				break;
			case T__40:
				enterOuterAlt(_localctx, 2);
				{
				setState(378);
				match(T__40);
				setState(379);
				expression(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstraintContext extends ParserRuleContext {
		public ConstraintContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constraint; }
	 
		public ConstraintContext() { }
		public void copyFrom(ConstraintContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class HardConstraintContext extends ConstraintContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public HardConstraintContext(ConstraintContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitHardConstraint(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SoftConstraintContext extends ConstraintContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public SoftConstraintContext(ConstraintContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitSoftConstraint(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ConstraintContext constraint() throws RecognitionException {
		ConstraintContext _localctx = new ConstraintContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_constraint);
		try {
			setState(395);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__41:
				_localctx = new HardConstraintContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(382);
				match(T__41);
				setState(385);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,45,_ctx) ) {
				case 1:
					{
					setState(383);
					match(Identifier);
					setState(384);
					match(T__9);
					}
					break;
				}
				setState(387);
				expression(0);
				}
				break;
			case T__42:
				_localctx = new SoftConstraintContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(388);
				match(T__42);
				setState(389);
				match(T__41);
				setState(392);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,46,_ctx) ) {
				case 1:
					{
					setState(390);
					match(Identifier);
					setState(391);
					match(T__9);
					}
					break;
				}
				setState(394);
				expression(0);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MultiplicityContext extends ParserRuleContext {
		public List<ExpressionOrStarContext> expressionOrStar() {
			return getRuleContexts(ExpressionOrStarContext.class);
		}
		public ExpressionOrStarContext expressionOrStar(int i) {
			return getRuleContext(ExpressionOrStarContext.class,i);
		}
		public MultiplicityContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_multiplicity; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitMultiplicity(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MultiplicityContext multiplicity() throws RecognitionException {
		MultiplicityContext _localctx = new MultiplicityContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_multiplicity);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(397);
			match(T__15);
			setState(398);
			expressionOrStar();
			setState(401);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__16) {
				{
				setState(399);
				match(T__16);
				setState(400);
				expressionOrStar();
				}
			}

			setState(403);
			match(T__17);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionOrStarContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExpressionOrStarContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionOrStar; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitExpressionOrStar(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionOrStarContext expressionOrStar() throws RecognitionException {
		ExpressionOrStarContext _localctx = new ExpressionOrStarContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_expressionOrStar);
		try {
			setState(407);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__1:
			case T__6:
			case T__7:
			case T__11:
			case T__44:
			case T__46:
			case T__47:
			case T__48:
			case T__49:
			case T__50:
			case T__51:
			case T__52:
			case T__53:
			case T__54:
			case T__55:
			case T__56:
			case T__57:
			case T__58:
			case T__59:
			case T__60:
			case T__61:
			case T__62:
			case T__63:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__76:
			case T__78:
			case T__80:
			case T__92:
			case T__113:
			case T__114:
			case T__116:
			case T__117:
			case T__118:
			case T__119:
			case T__120:
			case T__121:
			case T__123:
			case DateLiteral:
			case DurationLiteral:
			case IntegerLiteral:
			case RealLiteral:
			case BooleanLiteral:
			case NullLiteral:
			case ThisLiteral:
			case CharacterLiteral:
			case StringLiteral:
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(405);
				expression(0);
				}
				break;
			case T__5:
				enterOuterAlt(_localctx, 2);
				{
				setState(406);
				match(T__5);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeContext extends ParserRuleContext {
		public TypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_type; }
	 
		public TypeContext() { }
		public void copyFrom(TypeContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class ArrayTypeContext extends TypeContext {
		public ClassIdentifierContext classIdentifier() {
			return getRuleContext(ClassIdentifierContext.class,0);
		}
		public ArrayTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitArrayType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SubTypeContext extends TypeContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TerminalNode SUCHTHAT() { return getToken(ModelParser.SUCHTHAT, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public SubTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitSubType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IdentTypeContext extends TypeContext {
		public ClassIdentifierContext classIdentifier() {
			return getRuleContext(ClassIdentifierContext.class,0);
		}
		public TypeArgumentsContext typeArguments() {
			return getRuleContext(TypeArgumentsContext.class,0);
		}
		public IdentTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIdentType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class FuncTypeContext extends TypeContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public FuncTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitFuncType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class CartesianTypeContext extends TypeContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public CartesianTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitCartesianType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class PrimTypeContext extends TypeContext {
		public PrimitiveTypeContext primitiveType() {
			return getRuleContext(PrimitiveTypeContext.class,0);
		}
		public PrimTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPrimType(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ParenTypeContext extends TypeContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ParenTypeContext(TypeContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitParenType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeContext type() throws RecognitionException {
		return type(0);
	}

	private TypeContext type(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		TypeContext _localctx = new TypeContext(_ctx, _parentState);
		TypeContext _prevctx = _localctx;
		int _startState = 62;
		enterRecursionRule(_localctx, 62, RULE_type, _p);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(434);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				_localctx = new PrimTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(410);
				primitiveType();
				}
				break;
			case 2:
				{
				_localctx = new IdentTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(411);
				classIdentifier();
				setState(413);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,50,_ctx) ) {
				case 1:
					{
					setState(412);
					typeArguments();
					}
					break;
				}
				}
				break;
			case 3:
				{
				_localctx = new ArrayTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(415);
				classIdentifier();
				setState(418); 
				_errHandler.sync(this);
				_alt = 1;
				do {
					switch (_alt) {
					case 1:
						{
						{
						setState(416);
						match(T__15);
						setState(417);
						match(T__17);
						}
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(420); 
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,51,_ctx);
				} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
				}
				break;
			case 4:
				{
				_localctx = new ParenTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(422);
				match(T__11);
				setState(423);
				type(0);
				setState(424);
				match(T__12);
				}
				break;
			case 5:
				{
				_localctx = new SubTypeContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(426);
				match(T__44);
				setState(427);
				match(Identifier);
				setState(428);
				match(T__9);
				setState(429);
				type(0);
				setState(430);
				match(SUCHTHAT);
				setState(431);
				expression(0);
				setState(432);
				match(T__45);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(448);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(446);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
					case 1:
						{
						_localctx = new FuncTypeContext(new TypeContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_type);
						setState(436);
						if (!(precpred(_ctx, 3))) throw new FailedPredicateException(this, "precpred(_ctx, 3)");
						setState(437);
						match(T__43);
						setState(438);
						type(4);
						}
						break;
					case 2:
						{
						_localctx = new CartesianTypeContext(new TypeContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_type);
						setState(439);
						if (!(precpred(_ctx, 4))) throw new FailedPredicateException(this, "precpred(_ctx, 4)");
						setState(442); 
						_errHandler.sync(this);
						_alt = 1;
						do {
							switch (_alt) {
							case 1:
								{
								{
								setState(440);
								match(T__5);
								setState(441);
								type(0);
								}
								}
								break;
							default:
								throw new NoViableAltException(this);
							}
							setState(444); 
							_errHandler.sync(this);
							_alt = getInterpreter().adaptivePredict(_input,53,_ctx);
						} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
						}
						break;
					}
					} 
				}
				setState(450);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,55,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class PrimitiveTypeContext extends ParserRuleContext {
		public TerminalNode IntegerLiteral() { return getToken(ModelParser.IntegerLiteral, 0); }
		public PrimitiveTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_primitiveType; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPrimitiveType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PrimitiveTypeContext primitiveType() throws RecognitionException {
		PrimitiveTypeContext _localctx = new PrimitiveTypeContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_primitiveType);
		try {
			setState(473);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__46:
				enterOuterAlt(_localctx, 1);
				{
				setState(451);
				match(T__46);
				}
				break;
			case T__47:
				enterOuterAlt(_localctx, 2);
				{
				setState(452);
				match(T__47);
				}
				break;
			case T__48:
				enterOuterAlt(_localctx, 3);
				{
				setState(453);
				match(T__48);
				}
				break;
			case T__49:
				enterOuterAlt(_localctx, 4);
				{
				setState(454);
				match(T__49);
				}
				break;
			case T__50:
				enterOuterAlt(_localctx, 5);
				{
				setState(455);
				match(T__50);
				}
				break;
			case T__51:
				enterOuterAlt(_localctx, 6);
				{
				setState(456);
				match(T__51);
				}
				break;
			case T__52:
				enterOuterAlt(_localctx, 7);
				{
				setState(457);
				match(T__52);
				}
				break;
			case T__53:
				enterOuterAlt(_localctx, 8);
				{
				setState(458);
				match(T__53);
				}
				break;
			case T__54:
				enterOuterAlt(_localctx, 9);
				{
				setState(459);
				match(T__54);
				setState(460);
				match(T__15);
				setState(461);
				match(IntegerLiteral);
				setState(462);
				match(T__17);
				}
				break;
			case T__55:
				enterOuterAlt(_localctx, 10);
				{
				setState(463);
				match(T__55);
				}
				break;
			case T__56:
				enterOuterAlt(_localctx, 11);
				{
				setState(464);
				match(T__56);
				}
				break;
			case T__57:
				enterOuterAlt(_localctx, 12);
				{
				setState(465);
				match(T__57);
				}
				break;
			case T__58:
				enterOuterAlt(_localctx, 13);
				{
				setState(466);
				match(T__58);
				}
				break;
			case T__59:
				enterOuterAlt(_localctx, 14);
				{
				setState(467);
				match(T__59);
				}
				break;
			case T__60:
				enterOuterAlt(_localctx, 15);
				{
				setState(468);
				match(T__60);
				}
				break;
			case T__61:
				enterOuterAlt(_localctx, 16);
				{
				setState(469);
				match(T__61);
				}
				break;
			case T__62:
				enterOuterAlt(_localctx, 17);
				{
				setState(470);
				match(T__62);
				}
				break;
			case T__63:
				enterOuterAlt(_localctx, 18);
				{
				setState(471);
				match(T__63);
				}
				break;
			case T__64:
				enterOuterAlt(_localctx, 19);
				{
				setState(472);
				match(T__64);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ClassIdentifierContext extends ParserRuleContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public CollectionKindContext collectionKind() {
			return getRuleContext(CollectionKindContext.class,0);
		}
		public ClassIdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_classIdentifier; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitClassIdentifier(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ClassIdentifierContext classIdentifier() throws RecognitionException {
		ClassIdentifierContext _localctx = new ClassIdentifierContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_classIdentifier);
		try {
			setState(478);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case Identifier:
				enterOuterAlt(_localctx, 1);
				{
				setState(475);
				qualifiedName();
				}
				break;
			case T__65:
				enterOuterAlt(_localctx, 2);
				{
				setState(476);
				match(T__65);
				}
				break;
			case T__66:
			case T__67:
			case T__68:
			case T__69:
				enterOuterAlt(_localctx, 3);
				{
				setState(477);
				collectionKind();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CollectionKindContext extends ParserRuleContext {
		public CollectionKindContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collectionKind; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitCollectionKind(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CollectionKindContext collectionKind() throws RecognitionException {
		CollectionKindContext _localctx = new CollectionKindContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_collectionKind);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(480);
			_la = _input.LA(1);
			if ( !(((((_la - 67)) & ~0x3f) == 0 && ((1L << (_la - 67)) & ((1L << (T__66 - 67)) | (1L << (T__67 - 67)) | (1L << (T__68 - 67)) | (1L << (T__69 - 67)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class TypeArgumentsContext extends ParserRuleContext {
		public List<TypeContext> type() {
			return getRuleContexts(TypeContext.class);
		}
		public TypeContext type(int i) {
			return getRuleContext(TypeContext.class,i);
		}
		public TypeArgumentsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typeArguments; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeArguments(this);
			else return visitor.visitChildren(this);
		}
	}

	public final TypeArgumentsContext typeArguments() throws RecognitionException {
		TypeArgumentsContext _localctx = new TypeArgumentsContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_typeArguments);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(482);
			match(T__15);
			setState(483);
			type(0);
			setState(488);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(484);
				match(T__16);
				setState(485);
				type(0);
				}
				}
				setState(490);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(491);
			match(T__17);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	 
		public ExpressionContext() { }
		public void copyFrom(ExpressionContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class LiteralExpContext extends ExpressionContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitLiteralExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NotExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NotExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNotExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AndExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public AndExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAndExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ExistsExpContext extends ExpressionContext {
		public RngBindingListContext rngBindingList() {
			return getRuleContext(RngBindingListContext.class,0);
		}
		public TerminalNode SUCHTHAT() { return getToken(ModelParser.SUCHTHAT, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ExistsExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitExistsExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SetEnumExpContext extends ExpressionContext {
		public CollectionKindContext collectionKind() {
			return getRuleContext(CollectionKindContext.class,0);
		}
		public ExpressionListContext expressionList() {
			return getRuleContext(ExpressionListContext.class,0);
		}
		public SetEnumExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitSetEnumExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IndexExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public PositionalArgumentListContext positionalArgumentList() {
			return getRuleContext(PositionalArgumentListContext.class,0);
		}
		public IndexExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIndexExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BreakExpContext extends ExpressionContext {
		public BreakExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBreakExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class PrevExpContext extends ExpressionContext {
		public QualifiedNameContext qualifiedName() {
			return getRuleContext(QualifiedNameContext.class,0);
		}
		public PrevExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPrevExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BlockExpContext extends ExpressionContext {
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public BlockExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBlockExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BinOp2ExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BinOp2ExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBinOp2Exp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class TupleExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public TupleExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTupleExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class WhileExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public WhileExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitWhileExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SetRngExpContext extends ExpressionContext {
		public CollectionKindContext collectionKind() {
			return getRuleContext(CollectionKindContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public SetRngExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitSetRngExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IfExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public IfExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIfExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NegExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NegExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNegExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class OrExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public OrExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitOrExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AppExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public AppExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAppExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class TypeCheckExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TypeCheckExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeCheckExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ReturnExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitReturnExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IFFExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public IFFExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIFFExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ConstructorAppExp2Context extends ExpressionContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public ConstructorAppExp2Context(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitConstructorAppExp2(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class SetCompExpContext extends ExpressionContext {
		public CollectionKindContext collectionKind() {
			return getRuleContext(CollectionKindContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public RngBindingListContext rngBindingList() {
			return getRuleContext(RngBindingListContext.class,0);
		}
		public TerminalNode SUCHTHAT() { return getToken(ModelParser.SUCHTHAT, 0); }
		public SetCompExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitSetCompExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ConstructorAppExp1Context extends ExpressionContext {
		public ClassIdentifierContext classIdentifier() {
			return getRuleContext(ClassIdentifierContext.class,0);
		}
		public TypeArgumentsContext typeArguments() {
			return getRuleContext(TypeArgumentsContext.class,0);
		}
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public ConstructorAppExp1Context(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitConstructorAppExp1(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NewExpContext extends ExpressionContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ArgumentListContext argumentList() {
			return getRuleContext(ArgumentListContext.class,0);
		}
		public NewExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNewExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ResultExpContext extends ExpressionContext {
		public ResultExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitResultExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AssertExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AssertExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAssertExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class LambdaExpContext extends ExpressionContext {
		public PatternContext pattern() {
			return getRuleContext(PatternContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public LambdaExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitLambdaExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BitNotExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public BitNotExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBitNotExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BinOp1ExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BinOp1ExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBinOp1Exp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ForallExpContext extends ExpressionContext {
		public RngBindingListContext rngBindingList() {
			return getRuleContext(RngBindingListContext.class,0);
		}
		public TerminalNode SUCHTHAT() { return getToken(ModelParser.SUCHTHAT, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ForallExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitForallExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BinOp3ExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BinOp3ExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBinOp3Exp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IdentExpContext extends ExpressionContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public PrimitiveTypeContext primitiveType() {
			return getRuleContext(PrimitiveTypeContext.class,0);
		}
		public ImportLanguageContext importLanguage() {
			return getRuleContext(ImportLanguageContext.class,0);
		}
		public IdentExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIdentExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class DotExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public DotExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitDotExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ForExpContext extends ExpressionContext {
		public PatternContext pattern() {
			return getRuleContext(PatternContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ForExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitForExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class AssignExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public AssignExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitAssignExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ContinueExpContext extends ExpressionContext {
		public ContinueExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitContinueExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class MatchExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<MatchContext> match() {
			return getRuleContexts(MatchContext.class);
		}
		public MatchContext match(int i) {
			return getRuleContext(MatchContext.class,i);
		}
		public MatchExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitMatchExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class BitOpExpContext extends ExpressionContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public BitOpExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitBitOpExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ParenExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ParenExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitParenExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ClassExpContext extends ExpressionContext {
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public ClassExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitClassExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class ThisOuterExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TerminalNode ThisLiteral() { return getToken(ModelParser.ThisLiteral, 0); }
		public ThisOuterExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitThisOuterExp(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class TypeCastExpContext extends ExpressionContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TypeCastExpContext(ExpressionContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypeCastExp(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 72;
		enterRecursionRule(_localctx, 72, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(631);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,67,_ctx) ) {
			case 1:
				{
				_localctx = new ConstructorAppExp1Context(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(494);
				classIdentifier();
				setState(495);
				typeArguments();
				setState(496);
				match(T__11);
				setState(498);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__11) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
					{
					setState(497);
					argumentList();
					}
				}

				setState(500);
				match(T__12);
				}
				break;
			case 2:
				{
				_localctx = new ParenExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(502);
				match(T__11);
				setState(503);
				expression(0);
				setState(504);
				match(T__12);
				}
				break;
			case 3:
				{
				_localctx = new TupleExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(506);
				match(T__70);
				setState(507);
				match(T__11);
				setState(508);
				expression(0);
				setState(511); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(509);
					match(T__16);
					setState(510);
					expression(0);
					}
					}
					setState(513); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==T__16 );
				setState(515);
				match(T__12);
				}
				break;
			case 4:
				{
				_localctx = new LiteralExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(517);
				literal();
				}
				break;
			case 5:
				{
				_localctx = new IdentExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(518);
				match(Identifier);
				}
				break;
			case 6:
				{
				_localctx = new IdentExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(519);
				primitiveType();
				}
				break;
			case 7:
				{
				_localctx = new IdentExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(520);
				importLanguage();
				}
				break;
			case 8:
				{
				_localctx = new ClassExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(521);
				type(0);
				setState(522);
				match(T__4);
				setState(523);
				match(T__13);
				}
				break;
			case 9:
				{
				_localctx = new ConstructorAppExp2Context(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(525);
				type(0);
				setState(526);
				match(T__11);
				setState(528);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__11) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
					{
					setState(527);
					argumentList();
					}
				}

				setState(530);
				match(T__12);
				}
				break;
			case 10:
				{
				_localctx = new NewExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(532);
				match(T__71);
				setState(533);
				type(0);
				setState(534);
				match(T__11);
				setState(536);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__11) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
					{
					setState(535);
					argumentList();
					}
				}

				setState(538);
				match(T__12);
				}
				break;
			case 11:
				{
				_localctx = new NotExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(540);
				match(T__72);
				setState(541);
				expression(30);
				}
				break;
			case 12:
				{
				_localctx = new BlockExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(542);
				match(T__1);
				setState(543);
				block();
				setState(544);
				match(T__2);
				setState(545);
				if (!(_localctx.parent instanceof ModelParser.ModelContext)) throw new FailedPredicateException(this, "$ctx.parent instanceof ModelParser.ModelContext");
				}
				break;
			case 13:
				{
				_localctx = new IfExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(547);
				match(T__73);
				setState(548);
				expression(0);
				setState(549);
				match(T__74);
				setState(550);
				expression(0);
				setState(553);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,63,_ctx) ) {
				case 1:
					{
					setState(551);
					match(T__75);
					setState(552);
					expression(0);
					}
					break;
				}
				}
				break;
			case 14:
				{
				_localctx = new MatchExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(555);
				match(T__76);
				setState(556);
				expression(0);
				setState(557);
				match(T__77);
				setState(559); 
				_errHandler.sync(this);
				_alt = 1;
				do {
					switch (_alt) {
					case 1:
						{
						{
						setState(558);
						match();
						}
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(561); 
					_errHandler.sync(this);
					_alt = getInterpreter().adaptivePredict(_input,64,_ctx);
				} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
				}
				break;
			case 15:
				{
				_localctx = new WhileExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(563);
				match(T__78);
				setState(564);
				expression(0);
				setState(565);
				match(T__79);
				setState(566);
				expression(26);
				}
				break;
			case 16:
				{
				_localctx = new ForExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(568);
				match(T__80);
				setState(569);
				pattern(0);
				setState(570);
				match(T__81);
				setState(571);
				expression(0);
				setState(572);
				match(T__79);
				setState(573);
				expression(25);
				}
				break;
			case 17:
				{
				_localctx = new SetEnumExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(575);
				collectionKind();
				setState(576);
				match(T__1);
				setState(578);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__11) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
					{
					setState(577);
					expressionList();
					}
				}

				setState(580);
				match(T__2);
				}
				break;
			case 18:
				{
				_localctx = new SetRngExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(582);
				collectionKind();
				setState(583);
				match(T__1);
				setState(584);
				expression(0);
				setState(585);
				match(T__82);
				setState(586);
				expression(0);
				setState(587);
				match(T__2);
				}
				break;
			case 19:
				{
				_localctx = new SetCompExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(589);
				collectionKind();
				setState(590);
				match(T__1);
				setState(591);
				expression(0);
				setState(592);
				match(T__83);
				setState(593);
				rngBindingList();
				setState(594);
				match(SUCHTHAT);
				setState(595);
				expression(0);
				setState(596);
				match(T__2);
				}
				break;
			case 20:
				{
				_localctx = new AssertExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(598);
				match(T__113);
				setState(599);
				match(T__11);
				setState(600);
				expression(0);
				setState(601);
				match(T__12);
				}
				break;
			case 21:
				{
				_localctx = new NegExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(603);
				match(T__92);
				setState(604);
				expression(10);
				}
				break;
			case 22:
				{
				_localctx = new BitNotExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(605);
				match(T__114);
				setState(606);
				expression(9);
				}
				break;
			case 23:
				{
				_localctx = new PrevExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(607);
				qualifiedName();
				setState(608);
				match(T__115);
				}
				break;
			case 24:
				{
				_localctx = new ForallExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(610);
				match(T__116);
				setState(611);
				rngBindingList();
				setState(612);
				match(SUCHTHAT);
				setState(613);
				expression(7);
				}
				break;
			case 25:
				{
				_localctx = new ExistsExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(615);
				match(T__117);
				setState(616);
				rngBindingList();
				setState(617);
				match(SUCHTHAT);
				setState(618);
				expression(6);
				}
				break;
			case 26:
				{
				_localctx = new LambdaExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(620);
				pattern(0);
				setState(621);
				match(T__43);
				setState(622);
				expression(5);
				}
				break;
			case 27:
				{
				_localctx = new ContinueExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(624);
				match(T__118);
				}
				break;
			case 28:
				{
				_localctx = new BreakExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(625);
				match(T__119);
				}
				break;
			case 29:
				{
				_localctx = new ReturnExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(626);
				match(T__120);
				setState(628);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,66,_ctx) ) {
				case 1:
					{
					setState(627);
					expression(0);
					}
					break;
				}
				}
				break;
			case 30:
				{
				_localctx = new ResultExpContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(630);
				match(T__121);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(682);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,70,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(680);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,69,_ctx) ) {
					case 1:
						{
						_localctx = new BinOp1ExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(633);
						if (!(precpred(_ctx, 19))) throw new FailedPredicateException(this, "precpred(_ctx, 19)");
						setState(634);
						_la = _input.LA(1);
						if ( !(_la==T__5 || ((((_la - 86)) & ~0x3f) == 0 && ((1L << (_la - 86)) & ((1L << (T__85 - 86)) | (1L << (T__86 - 86)) | (1L << (T__87 - 86)) | (1L << (T__88 - 86)) | (1L << (T__89 - 86)) | (1L << (T__90 - 86)) | (1L << (T__91 - 86)))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(635);
						expression(20);
						}
						break;
					case 2:
						{
						_localctx = new BinOp2ExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(636);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(637);
						_la = _input.LA(1);
						if ( !(_la==T__18 || _la==T__92 || _la==T__93) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(638);
						expression(19);
						}
						break;
					case 3:
						{
						_localctx = new BitOpExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(639);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(640);
						_la = _input.LA(1);
						if ( !(((((_la - 95)) & ~0x3f) == 0 && ((1L << (_la - 95)) & ((1L << (T__94 - 95)) | (1L << (T__95 - 95)) | (1L << (T__96 - 95)) | (1L << (T__97 - 95)) | (1L << (T__98 - 95)) | (1L << (T__99 - 95)))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(641);
						expression(18);
						}
						break;
					case 4:
						{
						_localctx = new BinOp3ExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(642);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(643);
						_la = _input.LA(1);
						if ( !(_la==T__29 || ((((_la - 101)) & ~0x3f) == 0 && ((1L << (_la - 101)) & ((1L << (T__100 - 101)) | (1L << (T__101 - 101)) | (1L << (T__102 - 101)) | (1L << (T__103 - 101)) | (1L << (T__104 - 101)) | (1L << (T__105 - 101)) | (1L << (T__106 - 101)) | (1L << (T__107 - 101)) | (1L << (T__108 - 101)))) != 0)) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(644);
						expression(17);
						}
						break;
					case 5:
						{
						_localctx = new AndExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(645);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(646);
						match(T__109);
						setState(647);
						expression(16);
						}
						break;
					case 6:
						{
						_localctx = new OrExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(648);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(649);
						match(T__110);
						setState(650);
						expression(15);
						}
						break;
					case 7:
						{
						_localctx = new IFFExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(651);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(652);
						_la = _input.LA(1);
						if ( !(_la==T__111 || _la==T__112) ) {
						_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(653);
						expression(14);
						}
						break;
					case 8:
						{
						_localctx = new AssignExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(654);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(655);
						match(T__30);
						setState(656);
						expression(13);
						}
						break;
					case 9:
						{
						_localctx = new ThisOuterExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(657);
						if (!(precpred(_ctx, 37))) throw new FailedPredicateException(this, "precpred(_ctx, 37)");
						setState(658);
						match(T__4);
						setState(659);
						match(ThisLiteral);
						}
						break;
					case 10:
						{
						_localctx = new DotExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(660);
						if (!(precpred(_ctx, 36))) throw new FailedPredicateException(this, "precpred(_ctx, 36)");
						setState(661);
						match(T__4);
						setState(662);
						match(Identifier);
						}
						break;
					case 11:
						{
						_localctx = new AppExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(663);
						if (!(precpred(_ctx, 34))) throw new FailedPredicateException(this, "precpred(_ctx, 34)");
						setState(664);
						match(T__11);
						setState(666);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__1) | (1L << T__6) | (1L << T__7) | (1L << T__11) | (1L << T__44) | (1L << T__46) | (1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54) | (1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58) | (1L << T__59) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__66 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__73 - 64)) | (1L << (T__76 - 64)) | (1L << (T__78 - 64)) | (1L << (T__80 - 64)) | (1L << (T__92 - 64)) | (1L << (T__113 - 64)) | (1L << (T__114 - 64)) | (1L << (T__116 - 64)) | (1L << (T__117 - 64)) | (1L << (T__118 - 64)) | (1L << (T__119 - 64)) | (1L << (T__120 - 64)) | (1L << (T__121 - 64)) | (1L << (T__123 - 64)) | (1L << (DateLiteral - 64)))) != 0) || ((((_la - 128)) & ~0x3f) == 0 && ((1L << (_la - 128)) & ((1L << (DurationLiteral - 128)) | (1L << (IntegerLiteral - 128)) | (1L << (RealLiteral - 128)) | (1L << (BooleanLiteral - 128)) | (1L << (NullLiteral - 128)) | (1L << (ThisLiteral - 128)) | (1L << (CharacterLiteral - 128)) | (1L << (StringLiteral - 128)) | (1L << (Identifier - 128)))) != 0)) {
							{
							setState(665);
							argumentList();
							}
						}

						setState(668);
						match(T__12);
						}
						break;
					case 12:
						{
						_localctx = new IndexExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(669);
						if (!(precpred(_ctx, 31))) throw new FailedPredicateException(this, "precpred(_ctx, 31)");
						setState(670);
						match(T__15);
						setState(671);
						positionalArgumentList();
						setState(672);
						match(T__17);
						}
						break;
					case 13:
						{
						_localctx = new TypeCheckExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(674);
						if (!(precpred(_ctx, 21))) throw new FailedPredicateException(this, "precpred(_ctx, 21)");
						setState(675);
						match(T__84);
						setState(676);
						type(0);
						}
						break;
					case 14:
						{
						_localctx = new TypeCastExpContext(new ExpressionContext(_parentctx, _parentState));
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(677);
						if (!(precpred(_ctx, 20))) throw new FailedPredicateException(this, "precpred(_ctx, 20)");
						setState(678);
						match(T__23);
						setState(679);
						type(0);
						}
						break;
					}
					} 
				}
				setState(684);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,70,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class MatchContext extends ParserRuleContext {
		public List<PatternContext> pattern() {
			return getRuleContexts(PatternContext.class);
		}
		public PatternContext pattern(int i) {
			return getRuleContext(PatternContext.class,i);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public MatchContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_match; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitMatch(this);
			else return visitor.visitChildren(this);
		}
	}

	public final MatchContext match() throws RecognitionException {
		MatchContext _localctx = new MatchContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_match);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(685);
			match(T__122);
			setState(686);
			pattern(0);
			setState(691);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__83) {
				{
				{
				setState(687);
				match(T__83);
				setState(688);
				pattern(0);
				}
				}
				setState(693);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(694);
			match(T__111);
			setState(695);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArgumentListContext extends ParserRuleContext {
		public ArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_argumentList; }
	 
		public ArgumentListContext() { }
		public void copyFrom(ArgumentListContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class PosArgListContext extends ArgumentListContext {
		public PositionalArgumentListContext positionalArgumentList() {
			return getRuleContext(PositionalArgumentListContext.class,0);
		}
		public PosArgListContext(ArgumentListContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPosArgList(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class NamedArgListContext extends ArgumentListContext {
		public NamedArgumentListContext namedArgumentList() {
			return getRuleContext(NamedArgumentListContext.class,0);
		}
		public NamedArgListContext(ArgumentListContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNamedArgList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ArgumentListContext argumentList() throws RecognitionException {
		ArgumentListContext _localctx = new ArgumentListContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_argumentList);
		try {
			setState(699);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,72,_ctx) ) {
			case 1:
				_localctx = new PosArgListContext(_localctx);
				enterOuterAlt(_localctx, 1);
				{
				setState(697);
				positionalArgumentList();
				}
				break;
			case 2:
				_localctx = new NamedArgListContext(_localctx);
				enterOuterAlt(_localctx, 2);
				{
				setState(698);
				namedArgumentList();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PositionalArgumentListContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public PositionalArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_positionalArgumentList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPositionalArgumentList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PositionalArgumentListContext positionalArgumentList() throws RecognitionException {
		PositionalArgumentListContext _localctx = new PositionalArgumentListContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_positionalArgumentList);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(701);
			expression(0);
			setState(710);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(705);
					_errHandler.sync(this);
					switch (_input.LA(1)) {
					case T__16:
						{
						setState(702);
						match(T__16);
						}
						break;
					case T__17:
						{
						{
						setState(703);
						match(T__17);
						setState(704);
						match(T__15);
						}
						}
						break;
					default:
						throw new NoViableAltException(this);
					}
					setState(707);
					expression(0);
					}
					} 
				}
				setState(712);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,74,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NamedArgumentListContext extends ParserRuleContext {
		public List<NamedArgumentContext> namedArgument() {
			return getRuleContexts(NamedArgumentContext.class);
		}
		public NamedArgumentContext namedArgument(int i) {
			return getRuleContext(NamedArgumentContext.class,i);
		}
		public NamedArgumentListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namedArgumentList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNamedArgumentList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamedArgumentListContext namedArgumentList() throws RecognitionException {
		NamedArgumentListContext _localctx = new NamedArgumentListContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_namedArgumentList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(713);
			namedArgument();
			setState(718);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(714);
				match(T__16);
				setState(715);
				namedArgument();
				}
				}
				setState(720);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class NamedArgumentContext extends ParserRuleContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public NamedArgumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_namedArgument; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitNamedArgument(this);
			else return visitor.visitChildren(this);
		}
	}

	public final NamedArgumentContext namedArgument() throws RecognitionException {
		NamedArgumentContext _localctx = new NamedArgumentContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_namedArgument);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(721);
			match(Identifier);
			setState(722);
			match(T__22);
			setState(723);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CollectionOrTypeContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public CollectionOrTypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_collectionOrType; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitCollectionOrType(this);
			else return visitor.visitChildren(this);
		}
	}

	public final CollectionOrTypeContext collectionOrType() throws RecognitionException {
		CollectionOrTypeContext _localctx = new CollectionOrTypeContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_collectionOrType);
		try {
			setState(727);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,76,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(725);
				expression(0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(726);
				type(0);
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RngBindingListContext extends ParserRuleContext {
		public List<RngBindingContext> rngBinding() {
			return getRuleContexts(RngBindingContext.class);
		}
		public RngBindingContext rngBinding(int i) {
			return getRuleContext(RngBindingContext.class,i);
		}
		public RngBindingListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rngBindingList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitRngBindingList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RngBindingListContext rngBindingList() throws RecognitionException {
		RngBindingListContext _localctx = new RngBindingListContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_rngBindingList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(729);
			rngBinding();
			setState(734);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(730);
				match(T__16);
				setState(731);
				rngBinding();
				}
				}
				setState(736);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RngBindingContext extends ParserRuleContext {
		public PatternListContext patternList() {
			return getRuleContext(PatternListContext.class,0);
		}
		public CollectionOrTypeContext collectionOrType() {
			return getRuleContext(CollectionOrTypeContext.class,0);
		}
		public RngBindingContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_rngBinding; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitRngBinding(this);
			else return visitor.visitChildren(this);
		}
	}

	public final RngBindingContext rngBinding() throws RecognitionException {
		RngBindingContext _localctx = new RngBindingContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_rngBinding);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(737);
			patternList();
			setState(738);
			match(T__9);
			setState(739);
			collectionOrType();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PatternListContext extends ParserRuleContext {
		public List<PatternContext> pattern() {
			return getRuleContexts(PatternContext.class);
		}
		public PatternContext pattern(int i) {
			return getRuleContext(PatternContext.class,i);
		}
		public PatternListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_patternList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitPatternList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PatternListContext patternList() throws RecognitionException {
		PatternListContext _localctx = new PatternListContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_patternList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(741);
			pattern(0);
			setState(746);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(742);
				match(T__16);
				setState(743);
				pattern(0);
				}
				}
				setState(748);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PatternContext extends ParserRuleContext {
		public PatternContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_pattern; }
	 
		public PatternContext() { }
		public void copyFrom(PatternContext ctx) {
			super.copyFrom(ctx);
		}
	}
	public static class LiteralPatternContext extends PatternContext {
		public LiteralContext literal() {
			return getRuleContext(LiteralContext.class,0);
		}
		public LiteralPatternContext(PatternContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitLiteralPattern(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class DontCarePatternContext extends PatternContext {
		public DontCarePatternContext(PatternContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitDontCarePattern(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class IdentPatternContext extends PatternContext {
		public TerminalNode Identifier() { return getToken(ModelParser.Identifier, 0); }
		public IdentPatternContext(PatternContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIdentPattern(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class CartesianPatternContext extends PatternContext {
		public List<PatternContext> pattern() {
			return getRuleContexts(PatternContext.class);
		}
		public PatternContext pattern(int i) {
			return getRuleContext(PatternContext.class,i);
		}
		public CartesianPatternContext(PatternContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitCartesianPattern(this);
			else return visitor.visitChildren(this);
		}
	}
	public static class TypedPatternContext extends PatternContext {
		public PatternContext pattern() {
			return getRuleContext(PatternContext.class,0);
		}
		public TypeContext type() {
			return getRuleContext(TypeContext.class,0);
		}
		public TypedPatternContext(PatternContext ctx) { copyFrom(ctx); }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitTypedPattern(this);
			else return visitor.visitChildren(this);
		}
	}

	public final PatternContext pattern() throws RecognitionException {
		return pattern(0);
	}

	private PatternContext pattern(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		PatternContext _localctx = new PatternContext(_ctx, _parentState);
		PatternContext _prevctx = _localctx;
		int _startState = 92;
		enterRecursionRule(_localctx, 92, RULE_pattern, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(763);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DateLiteral:
			case DurationLiteral:
			case IntegerLiteral:
			case RealLiteral:
			case BooleanLiteral:
			case NullLiteral:
			case ThisLiteral:
			case CharacterLiteral:
			case StringLiteral:
				{
				_localctx = new LiteralPatternContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;

				setState(750);
				literal();
				}
				break;
			case T__123:
				{
				_localctx = new DontCarePatternContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(751);
				match(T__123);
				}
				break;
			case Identifier:
				{
				_localctx = new IdentPatternContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(752);
				match(Identifier);
				}
				break;
			case T__11:
				{
				_localctx = new CartesianPatternContext(_localctx);
				_ctx = _localctx;
				_prevctx = _localctx;
				setState(753);
				match(T__11);
				setState(754);
				pattern(0);
				setState(757); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(755);
					match(T__16);
					setState(756);
					pattern(0);
					}
					}
					setState(759); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( _la==T__16 );
				setState(761);
				match(T__12);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			_ctx.stop = _input.LT(-1);
			setState(770);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,81,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					{
					_localctx = new TypedPatternContext(new PatternContext(_parentctx, _parentState));
					pushNewRecursionContext(_localctx, _startState, RULE_pattern);
					setState(765);
					if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
					setState(766);
					match(T__9);
					setState(767);
					type(0);
					}
					} 
				}
				setState(772);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,81,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class IdentifierListContext extends ParserRuleContext {
		public List<TerminalNode> Identifier() { return getTokens(ModelParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(ModelParser.Identifier, i);
		}
		public IdentifierListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifierList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitIdentifierList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final IdentifierListContext identifierList() throws RecognitionException {
		IdentifierListContext _localctx = new IdentifierListContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_identifierList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(773);
			match(Identifier);
			setState(778);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(774);
				match(T__16);
				setState(775);
				match(Identifier);
				}
				}
				setState(780);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionListContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ExpressionListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expressionList; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitExpressionList(this);
			else return visitor.visitChildren(this);
		}
	}

	public final ExpressionListContext expressionList() throws RecognitionException {
		ExpressionListContext _localctx = new ExpressionListContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_expressionList);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(781);
			expression(0);
			setState(786);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__16) {
				{
				{
				setState(782);
				match(T__16);
				setState(783);
				expression(0);
				}
				}
				setState(788);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class QualifiedNameContext extends ParserRuleContext {
		public List<TerminalNode> Identifier() { return getTokens(ModelParser.Identifier); }
		public TerminalNode Identifier(int i) {
			return getToken(ModelParser.Identifier, i);
		}
		public QualifiedNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_qualifiedName; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitQualifiedName(this);
			else return visitor.visitChildren(this);
		}
	}

	public final QualifiedNameContext qualifiedName() throws RecognitionException {
		QualifiedNameContext _localctx = new QualifiedNameContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_qualifiedName);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(789);
			match(Identifier);
			setState(794);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,84,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(790);
					match(T__4);
					setState(791);
					match(Identifier);
					}
					} 
				}
				setState(796);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,84,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LiteralContext extends ParserRuleContext {
		public TerminalNode DateLiteral() { return getToken(ModelParser.DateLiteral, 0); }
		public TerminalNode DurationLiteral() { return getToken(ModelParser.DurationLiteral, 0); }
		public TerminalNode IntegerLiteral() { return getToken(ModelParser.IntegerLiteral, 0); }
		public TerminalNode RealLiteral() { return getToken(ModelParser.RealLiteral, 0); }
		public TerminalNode CharacterLiteral() { return getToken(ModelParser.CharacterLiteral, 0); }
		public TerminalNode StringLiteral() { return getToken(ModelParser.StringLiteral, 0); }
		public TerminalNode BooleanLiteral() { return getToken(ModelParser.BooleanLiteral, 0); }
		public TerminalNode NullLiteral() { return getToken(ModelParser.NullLiteral, 0); }
		public TerminalNode ThisLiteral() { return getToken(ModelParser.ThisLiteral, 0); }
		public LiteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literal; }
		@Override
		public <T> T accept(ParseTreeVisitor<? extends T> visitor) {
			if ( visitor instanceof ModelVisitor ) return ((ModelVisitor<? extends T>)visitor).visitLiteral(this);
			else return visitor.visitChildren(this);
		}
	}

	public final LiteralContext literal() throws RecognitionException {
		LiteralContext _localctx = new LiteralContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_literal);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(797);
			_la = _input.LA(1);
			if ( !(((((_la - 127)) & ~0x3f) == 0 && ((1L << (_la - 127)) & ((1L << (DateLiteral - 127)) | (1L << (DurationLiteral - 127)) | (1L << (IntegerLiteral - 127)) | (1L << (RealLiteral - 127)) | (1L << (BooleanLiteral - 127)) | (1L << (NullLiteral - 127)) | (1L << (ThisLiteral - 127)) | (1L << (CharacterLiteral - 127)) | (1L << (StringLiteral - 127)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 31:
			return type_sempred((TypeContext)_localctx, predIndex);
		case 36:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		case 46:
			return pattern_sempred((PatternContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean type_sempred(TypeContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 3);
		case 1:
			return precpred(_ctx, 4);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 2:
			return _localctx.parent instanceof ModelParser.ModelContext;
		case 3:
			return precpred(_ctx, 19);
		case 4:
			return precpred(_ctx, 18);
		case 5:
			return precpred(_ctx, 17);
		case 6:
			return precpred(_ctx, 16);
		case 7:
			return precpred(_ctx, 15);
		case 8:
			return precpred(_ctx, 14);
		case 9:
			return precpred(_ctx, 13);
		case 10:
			return precpred(_ctx, 12);
		case 11:
			return precpred(_ctx, 37);
		case 12:
			return precpred(_ctx, 36);
		case 13:
			return precpred(_ctx, 34);
		case 14:
			return precpred(_ctx, 31);
		case 15:
			return precpred(_ctx, 21);
		case 16:
			return precpred(_ctx, 20);
		}
		return true;
	}
	private boolean pattern_sempred(PatternContext _localctx, int predIndex) {
		switch (predIndex) {
		case 17:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\u0091\u0322\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\3\2\3\2\3\2\3\3\7\3m\n\3\f\3\16\3p\13\3\3\4\3\4\3\4\3\4\5\4v\n\4\3"+
		"\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u0082\n\5\3\6\3\6\5\6\u0086"+
		"\n\6\3\6\3\6\3\6\5\6\u008b\n\6\3\7\3\7\3\b\3\b\3\b\3\b\3\b\3\t\3\t\3\t"+
		"\3\t\3\t\3\t\5\t\u009a\n\t\3\n\7\n\u009d\n\n\f\n\16\n\u00a0\13\n\3\n\3"+
		"\n\7\n\u00a4\n\n\f\n\16\n\u00a7\13\n\3\n\5\n\u00aa\n\n\3\13\3\13\5\13"+
		"\u00ae\n\13\3\13\3\13\5\13\u00b2\n\13\3\13\5\13\u00b5\n\13\3\13\3\13\3"+
		"\13\3\13\5\13\u00bb\n\13\3\f\3\f\3\f\3\f\7\f\u00c1\n\f\f\f\16\f\u00c4"+
		"\13\f\3\f\3\f\3\r\3\r\3\r\5\r\u00cb\n\r\3\16\3\16\3\16\7\16\u00d0\n\16"+
		"\f\16\16\16\u00d3\13\16\3\17\3\17\3\17\3\17\7\17\u00d9\n\17\f\17\16\17"+
		"\u00dc\13\17\3\20\7\20\u00df\n\20\f\20\16\20\u00e2\13\20\3\21\7\21\u00e5"+
		"\n\21\f\21\16\21\u00e8\13\21\3\21\3\21\3\22\3\22\3\22\3\22\3\22\3\22\3"+
		"\22\3\22\3\22\3\22\5\22\u00f6\n\22\3\23\3\23\3\23\3\23\7\23\u00fc\n\23"+
		"\f\23\16\23\u00ff\13\23\3\23\5\23\u0102\n\23\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\5\24\u010b\n\24\3\25\3\25\3\25\3\25\5\25\u0111\n\25\3\26\3"+
		"\26\3\26\3\26\5\26\u0117\n\26\3\27\3\27\3\27\5\27\u011c\n\27\3\27\3\27"+
		"\5\27\u0120\n\27\3\30\6\30\u0123\n\30\r\30\16\30\u0124\3\30\3\30\3\30"+
		"\5\30\u012a\n\30\3\30\5\30\u012d\n\30\3\30\3\30\5\30\u0131\n\30\3\30\7"+
		"\30\u0134\n\30\f\30\16\30\u0137\13\30\3\30\3\30\3\30\3\30\5\30\u013d\n"+
		"\30\3\30\3\30\5\30\u0141\n\30\3\30\7\30\u0144\n\30\f\30\16\30\u0147\13"+
		"\30\3\30\3\30\5\30\u014b\n\30\3\30\3\30\5\30\u014f\n\30\3\31\3\31\3\32"+
		"\3\32\3\32\5\32\u0156\n\32\3\32\3\32\5\32\u015a\n\32\3\32\5\32\u015d\n"+
		"\32\3\32\3\32\5\32\u0161\n\32\3\32\7\32\u0164\n\32\f\32\16\32\u0167\13"+
		"\32\3\32\3\32\3\32\3\32\5\32\u016d\n\32\3\33\3\33\3\33\7\33\u0172\n\33"+
		"\f\33\16\33\u0175\13\33\3\34\3\34\3\34\3\34\3\35\3\35\3\35\3\35\5\35\u017f"+
		"\n\35\3\36\3\36\3\36\5\36\u0184\n\36\3\36\3\36\3\36\3\36\3\36\5\36\u018b"+
		"\n\36\3\36\5\36\u018e\n\36\3\37\3\37\3\37\3\37\5\37\u0194\n\37\3\37\3"+
		"\37\3 \3 \5 \u019a\n \3!\3!\3!\3!\5!\u01a0\n!\3!\3!\3!\6!\u01a5\n!\r!"+
		"\16!\u01a6\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\5!\u01b5\n!\3!\3!\3!\3"+
		"!\3!\3!\6!\u01bd\n!\r!\16!\u01be\7!\u01c1\n!\f!\16!\u01c4\13!\3\"\3\""+
		"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3\"\3"+
		"\"\3\"\3\"\5\"\u01dc\n\"\3#\3#\3#\5#\u01e1\n#\3$\3$\3%\3%\3%\3%\7%\u01e9"+
		"\n%\f%\16%\u01ec\13%\3%\3%\3&\3&\3&\3&\3&\5&\u01f5\n&\3&\3&\3&\3&\3&\3"+
		"&\3&\3&\3&\3&\3&\6&\u0202\n&\r&\16&\u0203\3&\3&\3&\3&\3&\3&\3&\3&\3&\3"+
		"&\3&\3&\3&\5&\u0213\n&\3&\3&\3&\3&\3&\3&\5&\u021b\n&\3&\3&\3&\3&\3&\3"+
		"&\3&\3&\3&\3&\3&\3&\3&\3&\3&\5&\u022c\n&\3&\3&\3&\3&\6&\u0232\n&\r&\16"+
		"&\u0233\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\5&\u0245\n&\3&\3"+
		"&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3"+
		"&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3"+
		"&\5&\u0277\n&\3&\5&\u027a\n&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3"+
		"&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\5&\u029d\n"+
		"&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\3&\7&\u02ab\n&\f&\16&\u02ae\13&\3\'"+
		"\3\'\3\'\3\'\7\'\u02b4\n\'\f\'\16\'\u02b7\13\'\3\'\3\'\3\'\3(\3(\5(\u02be"+
		"\n(\3)\3)\3)\3)\5)\u02c4\n)\3)\7)\u02c7\n)\f)\16)\u02ca\13)\3*\3*\3*\7"+
		"*\u02cf\n*\f*\16*\u02d2\13*\3+\3+\3+\3+\3,\3,\5,\u02da\n,\3-\3-\3-\7-"+
		"\u02df\n-\f-\16-\u02e2\13-\3.\3.\3.\3.\3/\3/\3/\7/\u02eb\n/\f/\16/\u02ee"+
		"\13/\3\60\3\60\3\60\3\60\3\60\3\60\3\60\3\60\6\60\u02f8\n\60\r\60\16\60"+
		"\u02f9\3\60\3\60\5\60\u02fe\n\60\3\60\3\60\3\60\7\60\u0303\n\60\f\60\16"+
		"\60\u0306\13\60\3\61\3\61\3\61\7\61\u030b\n\61\f\61\16\61\u030e\13\61"+
		"\3\62\3\62\3\62\7\62\u0313\n\62\f\62\16\62\u0316\13\62\3\63\3\63\3\63"+
		"\7\63\u031b\n\63\f\63\16\63\u031e\13\63\3\64\3\64\3\64\2\5@J^\65\2\4\6"+
		"\b\n\f\16\20\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRT"+
		"VXZ\\^`bdf\2\16\3\2\t\n\4\2\20\21\u008a\u008a\3\2\34\35\3\2 !\3\2\"(\3"+
		"\2EH\4\2\b\bX^\4\2\25\25_`\3\2af\4\2  go\3\2rs\3\2\u0081\u0089\2\u038d"+
		"\2h\3\2\2\2\4n\3\2\2\2\6u\3\2\2\2\b\u0081\3\2\2\2\n\u0083\3\2\2\2\f\u008c"+
		"\3\2\2\2\16\u008e\3\2\2\2\20\u0093\3\2\2\2\22\u00a9\3\2\2\2\24\u00ab\3"+
		"\2\2\2\26\u00bc\3\2\2\2\30\u00c7\3\2\2\2\32\u00cc\3\2\2\2\34\u00d4\3\2"+
		"\2\2\36\u00e0\3\2\2\2 \u00e6\3\2\2\2\"\u00f5\3\2\2\2$\u00f7\3\2\2\2&\u0103"+
		"\3\2\2\2(\u010c\3\2\2\2*\u0112\3\2\2\2,\u0118\3\2\2\2.\u014e\3\2\2\2\60"+
		"\u0150\3\2\2\2\62\u0152\3\2\2\2\64\u016e\3\2\2\2\66\u0176\3\2\2\28\u017e"+
		"\3\2\2\2:\u018d\3\2\2\2<\u018f\3\2\2\2>\u0199\3\2\2\2@\u01b4\3\2\2\2B"+
		"\u01db\3\2\2\2D\u01e0\3\2\2\2F\u01e2\3\2\2\2H\u01e4\3\2\2\2J\u0279\3\2"+
		"\2\2L\u02af\3\2\2\2N\u02bd\3\2\2\2P\u02bf\3\2\2\2R\u02cb\3\2\2\2T\u02d3"+
		"\3\2\2\2V\u02d9\3\2\2\2X\u02db\3\2\2\2Z\u02e3\3\2\2\2\\\u02e7\3\2\2\2"+
		"^\u02fd\3\2\2\2`\u0307\3\2\2\2b\u030f\3\2\2\2d\u0317\3\2\2\2f\u031f\3"+
		"\2\2\2hi\5\4\3\2ij\7\2\2\3j\3\3\2\2\2km\5\6\4\2lk\3\2\2\2mp\3\2\2\2nl"+
		"\3\2\2\2no\3\2\2\2o\5\3\2\2\2pn\3\2\2\2qv\5\b\5\2rv\5\n\6\2sv\5\16\b\2"+
		"tv\5\22\n\2uq\3\2\2\2ur\3\2\2\2us\3\2\2\2ut\3\2\2\2v\7\3\2\2\2wx\7\3\2"+
		"\2xy\5d\63\2yz\7\4\2\2z{\5\4\3\2{|\7\5\2\2|\u0082\3\2\2\2}~\7\3\2\2~\177"+
		"\5d\63\2\177\u0080\5\4\3\2\u0080\u0082\3\2\2\2\u0081w\3\2\2\2\u0081}\3"+
		"\2\2\2\u0082\t\3\2\2\2\u0083\u0085\7\6\2\2\u0084\u0086\5\f\7\2\u0085\u0084"+
		"\3\2\2\2\u0085\u0086\3\2\2\2\u0086\u0087\3\2\2\2\u0087\u008a\5d\63\2\u0088"+
		"\u0089\7\7\2\2\u0089\u008b\7\b\2\2\u008a\u0088\3\2\2\2\u008a\u008b\3\2"+
		"\2\2\u008b\13\3\2\2\2\u008c\u008d\t\2\2\2\u008d\r\3\2\2\2\u008e\u008f"+
		"\7\13\2\2\u008f\u0090\7\u008a\2\2\u0090\u0091\7\f\2\2\u0091\u0092\5@!"+
		"\2\u0092\17\3\2\2\2\u0093\u0094\7\r\2\2\u0094\u0099\7\u008a\2\2\u0095"+
		"\u0096\7\16\2\2\u0096\u0097\5J&\2\u0097\u0098\7\17\2\2\u0098\u009a\3\2"+
		"\2\2\u0099\u0095\3\2\2\2\u0099\u009a\3\2\2\2\u009a\21\3\2\2\2\u009b\u009d"+
		"\5\20\t\2\u009c\u009b\3\2\2\2\u009d\u00a0\3\2\2\2\u009e\u009c\3\2\2\2"+
		"\u009e\u009f\3\2\2\2\u009f\u00a1\3\2\2\2\u00a0\u009e\3\2\2\2\u00a1\u00aa"+
		"\5\24\13\2\u00a2\u00a4\5\20\t\2\u00a3\u00a2\3\2\2\2\u00a4\u00a7\3\2\2"+
		"\2\u00a5\u00a3\3\2\2\2\u00a5\u00a6\3\2\2\2\u00a6\u00a8\3\2\2\2\u00a7\u00a5"+
		"\3\2\2\2\u00a8\u00aa\5\"\22\2\u00a9\u009e\3\2\2\2\u00a9\u00a5\3\2\2\2"+
		"\u00aa\23\3\2\2\2\u00ab\u00ad\t\3\2\2\u00ac\u00ae\7\177\2\2\u00ad\u00ac"+
		"\3\2\2\2\u00ad\u00ae\3\2\2\2\u00ae\u00af\3\2\2\2\u00af\u00b1\7\u008a\2"+
		"\2\u00b0\u00b2\5\26\f\2\u00b1\u00b0\3\2\2\2\u00b1\u00b2\3\2\2\2\u00b2"+
		"\u00b4\3\2\2\2\u00b3\u00b5\5\34\17\2\u00b4\u00b3\3\2\2\2\u00b4\u00b5\3"+
		"\2\2\2\u00b5\u00ba\3\2\2\2\u00b6\u00b7\7\4\2\2\u00b7\u00b8\5\36\20\2\u00b8"+
		"\u00b9\7\5\2\2\u00b9\u00bb\3\2\2\2\u00ba\u00b6\3\2\2\2\u00ba\u00bb\3\2"+
		"\2\2\u00bb\25\3\2\2\2\u00bc\u00bd\7\22\2\2\u00bd\u00c2\5\30\r\2\u00be"+
		"\u00bf\7\23\2\2\u00bf\u00c1\5\30\r\2\u00c0\u00be\3\2\2\2\u00c1\u00c4\3"+
		"\2\2\2\u00c2\u00c0\3\2\2\2\u00c2\u00c3\3\2\2\2\u00c3\u00c5\3\2\2\2\u00c4"+
		"\u00c2\3\2\2\2\u00c5\u00c6\7\24\2\2\u00c6\27\3\2\2\2\u00c7\u00ca\7\u008a"+
		"\2\2\u00c8\u00c9\7\f\2\2\u00c9\u00cb\5\32\16\2\u00ca\u00c8\3\2\2\2\u00ca"+
		"\u00cb\3\2\2\2\u00cb\31\3\2\2\2\u00cc\u00d1\5@!\2\u00cd\u00ce\7\25\2\2"+
		"\u00ce\u00d0\5@!\2\u00cf\u00cd\3\2\2\2\u00d0\u00d3\3\2\2\2\u00d1\u00cf"+
		"\3\2\2\2\u00d1\u00d2\3\2\2\2\u00d2\33\3\2\2\2\u00d3\u00d1\3\2\2\2\u00d4"+
		"\u00d5\7\26\2\2\u00d5\u00da\5@!\2\u00d6\u00d7\7\23\2\2\u00d7\u00d9\5@"+
		"!\2\u00d8\u00d6\3\2\2\2\u00d9\u00dc\3\2\2\2\u00da\u00d8\3\2\2\2\u00da"+
		"\u00db\3\2\2\2\u00db\35\3\2\2\2\u00dc\u00da\3\2\2\2\u00dd\u00df\5 \21"+
		"\2\u00de\u00dd\3\2\2\2\u00df\u00e2\3\2\2\2\u00e0\u00de\3\2\2\2\u00e0\u00e1"+
		"\3\2\2\2\u00e1\37\3\2\2\2\u00e2\u00e0\3\2\2\2\u00e3\u00e5\5\20\t\2\u00e4"+
		"\u00e3\3\2\2\2\u00e5\u00e8\3\2\2\2\u00e6\u00e4\3\2\2\2\u00e6\u00e7\3\2"+
		"\2\2\u00e7\u00e9\3\2\2\2\u00e8\u00e6\3\2\2\2\u00e9\u00ea\5\"\22\2\u00ea"+
		"!\3\2\2\2\u00eb\u00f6\5,\27\2\u00ec\u00f6\5\24\13\2\u00ed\u00f6\5.\30"+
		"\2\u00ee\u00f6\5\62\32\2\u00ef\u00f6\5:\36\2\u00f0\u00f6\5*\26\2\u00f1"+
		"\u00f6\5$\23\2\u00f2\u00f6\5&\24\2\u00f3\u00f6\5(\25\2\u00f4\u00f6\5J"+
		"&\2\u00f5\u00eb\3\2\2\2\u00f5\u00ec\3\2\2\2\u00f5\u00ed\3\2\2\2\u00f5"+
		"\u00ee\3\2\2\2\u00f5\u00ef\3\2\2\2\u00f5\u00f0\3\2\2\2\u00f5\u00f1\3\2"+
		"\2\2\u00f5\u00f2\3\2\2\2\u00f5\u00f3\3\2\2\2\u00f5\u00f4\3\2\2\2\u00f6"+
		"#\3\2\2\2\u00f7\u00f8\7\27\2\2\u00f8\u00fd\5@!\2\u00f9\u00fa\7\23\2\2"+
		"\u00fa\u00fc\5@!\2\u00fb\u00f9\3\2\2\2\u00fc\u00ff\3\2\2\2\u00fd\u00fb"+
		"\3\2\2\2\u00fd\u00fe\3\2\2\2\u00fe\u0101\3\2\2\2\u00ff\u00fd\3\2\2\2\u0100"+
		"\u0102\7\u0091\2\2\u0101\u0100\3\2\2\2\u0101\u0102\3\2\2\2\u0102%\3\2"+
		"\2\2\u0103\u0104\7\30\2\2\u0104\u0105\5d\63\2\u0105\u0106\7\31\2\2\u0106"+
		"\u0107\7\u008a\2\2\u0107\u0108\7\32\2\2\u0108\u010a\7\u008a\2\2\u0109"+
		"\u010b\7\u0091\2\2\u010a\u0109\3\2\2\2\u010a\u010b\3\2\2\2\u010b\'\3\2"+
		"\2\2\u010c\u010d\7\33\2\2\u010d\u010e\5@!\2\u010e\u0110\7\u008a\2\2\u010f"+
		"\u0111\7\u0091\2\2\u0110\u010f\3\2\2\2\u0110\u0111\3\2\2\2\u0111)\3\2"+
		"\2\2\u0112\u0113\t\4\2\2\u0113\u0116\5J&\2\u0114\u0115\7\36\2\2\u0115"+
		"\u0117\7\u0083\2\2\u0116\u0114\3\2\2\2\u0116\u0117\3\2\2\2\u0117+\3\2"+
		"\2\2\u0118\u0119\7\37\2\2\u0119\u011f\7\u008a\2\2\u011a\u011c\5\26\f\2"+
		"\u011b\u011a\3\2\2\2\u011b\u011c\3\2\2\2\u011c\u011d\3\2\2\2\u011d\u011e"+
		"\7 \2\2\u011e\u0120\5@!\2\u011f\u011b\3\2\2\2\u011f\u0120\3\2\2\2\u0120"+
		"-\3\2\2\2\u0121\u0123\5\60\31\2\u0122\u0121\3\2\2\2\u0123\u0124\3\2\2"+
		"\2\u0124\u0122\3\2\2\2\u0124\u0125\3\2\2\2\u0125\u0126\3\2\2\2\u0126\u0129"+
		"\7\u008a\2\2\u0127\u0128\7\f\2\2\u0128\u012a\5@!\2\u0129\u0127\3\2\2\2"+
		"\u0129\u012a\3\2\2\2\u012a\u012c\3\2\2\2\u012b\u012d\5<\37\2\u012c\u012b"+
		"\3\2\2\2\u012c\u012d\3\2\2\2\u012d\u0130\3\2\2\2\u012e\u012f\t\5\2\2\u012f"+
		"\u0131\5J&\2\u0130\u012e\3\2\2\2\u0130\u0131\3\2\2\2\u0131\u014f\3\2\2"+
		"\2\u0132\u0134\5\60\31\2\u0133\u0132\3\2\2\2\u0134\u0137\3\2\2\2\u0135"+
		"\u0133\3\2\2\2\u0135\u0136\3\2\2\2\u0136\u0138\3\2\2\2\u0137\u0135\3\2"+
		"\2\2\u0138\u0139\7\u008a\2\2\u0139\u013a\7\f\2\2\u013a\u013c\5@!\2\u013b"+
		"\u013d\5<\37\2\u013c\u013b\3\2\2\2\u013c\u013d\3\2\2\2\u013d\u0140\3\2"+
		"\2\2\u013e\u013f\t\5\2\2\u013f\u0141\5J&\2\u0140\u013e\3\2\2\2\u0140\u0141"+
		"\3\2\2\2\u0141\u014f\3\2\2\2\u0142\u0144\5\60\31\2\u0143\u0142\3\2\2\2"+
		"\u0144\u0147\3\2\2\2\u0145\u0143\3\2\2\2\u0145\u0146\3\2\2\2\u0146\u0148"+
		"\3\2\2\2\u0147\u0145\3\2\2\2\u0148\u014a\7\u008a\2\2\u0149\u014b\5<\37"+
		"\2\u014a\u0149\3\2\2\2\u014a\u014b\3\2\2\2\u014b\u014c\3\2\2\2\u014c\u014d"+
		"\t\5\2\2\u014d\u014f\5J&\2\u014e\u0122\3\2\2\2\u014e\u0135\3\2\2\2\u014e"+
		"\u0145\3\2\2\2\u014f/\3\2\2\2\u0150\u0151\t\6\2\2\u0151\61\3\2\2\2\u0152"+
		"\u0153\7)\2\2\u0153\u0155\7\u008a\2\2\u0154\u0156\5\26\f\2\u0155\u0154"+
		"\3\2\2\2\u0155\u0156\3\2\2\2\u0156\u015c\3\2\2\2\u0157\u0159\7\16\2\2"+
		"\u0158\u015a\5\64\33\2\u0159\u0158\3\2\2\2\u0159\u015a\3\2\2\2\u015a\u015b"+
		"\3\2\2\2\u015b\u015d\7\17\2\2\u015c\u0157\3\2\2\2\u015c\u015d\3\2\2\2"+
		"\u015d\u0160\3\2\2\2\u015e\u015f\7\f\2\2\u015f\u0161\5@!\2\u0160\u015e"+
		"\3\2\2\2\u0160\u0161\3\2\2\2\u0161\u0165\3\2\2\2\u0162\u0164\58\35\2\u0163"+
		"\u0162\3\2\2\2\u0164\u0167\3\2\2\2\u0165\u0163\3\2\2\2\u0165\u0166\3\2"+
		"\2\2\u0166\u016c\3\2\2\2\u0167\u0165\3\2\2\2\u0168\u0169\7\4\2\2\u0169"+
		"\u016a\5\36\20\2\u016a\u016b\7\5\2\2\u016b\u016d\3\2\2\2\u016c\u0168\3"+
		"\2\2\2\u016c\u016d\3\2\2\2\u016d\63\3\2\2\2\u016e\u0173\5\66\34\2\u016f"+
		"\u0170\7\23\2\2\u0170\u0172\5\66\34\2\u0171\u016f\3\2\2\2\u0172\u0175"+
		"\3\2\2\2\u0173\u0171\3\2\2\2\u0173\u0174\3\2\2\2\u0174\65\3\2\2\2\u0175"+
		"\u0173\3\2\2\2\u0176\u0177\7\u008a\2\2\u0177\u0178\7\f\2\2\u0178\u0179"+
		"\5@!\2\u0179\67\3\2\2\2\u017a\u017b\7*\2\2\u017b\u017f\5J&\2\u017c\u017d"+
		"\7+\2\2\u017d\u017f\5J&\2\u017e\u017a\3\2\2\2\u017e\u017c\3\2\2\2\u017f"+
		"9\3\2\2\2\u0180\u0183\7,\2\2\u0181\u0182\7\u008a\2\2\u0182\u0184\7\f\2"+
		"\2\u0183\u0181\3\2\2\2\u0183\u0184\3\2\2\2\u0184\u0185\3\2\2\2\u0185\u018e"+
		"\5J&\2\u0186\u0187\7-\2\2\u0187\u018a\7,\2\2\u0188\u0189\7\u008a\2\2\u0189"+
		"\u018b\7\f\2\2\u018a\u0188\3\2\2\2\u018a\u018b\3\2\2\2\u018b\u018c\3\2"+
		"\2\2\u018c\u018e\5J&\2\u018d\u0180\3\2\2\2\u018d\u0186\3\2\2\2\u018e;"+
		"\3\2\2\2\u018f\u0190\7\22\2\2\u0190\u0193\5> \2\u0191\u0192\7\23\2\2\u0192"+
		"\u0194\5> \2\u0193\u0191\3\2\2\2\u0193\u0194\3\2\2\2\u0194\u0195\3\2\2"+
		"\2\u0195\u0196\7\24\2\2\u0196=\3\2\2\2\u0197\u019a\5J&\2\u0198\u019a\7"+
		"\b\2\2\u0199\u0197\3\2\2\2\u0199\u0198\3\2\2\2\u019a?\3\2\2\2\u019b\u019c"+
		"\b!\1\2\u019c\u01b5\5B\"\2\u019d\u019f\5D#\2\u019e\u01a0\5H%\2\u019f\u019e"+
		"\3\2\2\2\u019f\u01a0\3\2\2\2\u01a0\u01b5\3\2\2\2\u01a1\u01a4\5D#\2\u01a2"+
		"\u01a3\7\22\2\2\u01a3\u01a5\7\24\2\2\u01a4\u01a2\3\2\2\2\u01a5\u01a6\3"+
		"\2\2\2\u01a6\u01a4\3\2\2\2\u01a6\u01a7\3\2\2\2\u01a7\u01b5\3\2\2\2\u01a8"+
		"\u01a9\7\16\2\2\u01a9\u01aa\5@!\2\u01aa\u01ab\7\17\2\2\u01ab\u01b5\3\2"+
		"\2\2\u01ac\u01ad\7/\2\2\u01ad\u01ae\7\u008a\2\2\u01ae\u01af\7\f\2\2\u01af"+
		"\u01b0\5@!\2\u01b0\u01b1\7\u0080\2\2\u01b1\u01b2\5J&\2\u01b2\u01b3\7\60"+
		"\2\2\u01b3\u01b5\3\2\2\2\u01b4\u019b\3\2\2\2\u01b4\u019d\3\2\2\2\u01b4"+
		"\u01a1\3\2\2\2\u01b4\u01a8\3\2\2\2\u01b4\u01ac\3\2\2\2\u01b5\u01c2\3\2"+
		"\2\2\u01b6\u01b7\f\5\2\2\u01b7\u01b8\7.\2\2\u01b8\u01c1\5@!\6\u01b9\u01bc"+
		"\f\6\2\2\u01ba\u01bb\7\b\2\2\u01bb\u01bd\5@!\2\u01bc\u01ba\3\2\2\2\u01bd"+
		"\u01be\3\2\2\2\u01be\u01bc\3\2\2\2\u01be\u01bf\3\2\2\2\u01bf\u01c1\3\2"+
		"\2\2\u01c0\u01b6\3\2\2\2\u01c0\u01b9\3\2\2\2\u01c1\u01c4\3\2\2\2\u01c2"+
		"\u01c0\3\2\2\2\u01c2\u01c3\3\2\2\2\u01c3A\3\2\2\2\u01c4\u01c2\3\2\2\2"+
		"\u01c5\u01dc\7\61\2\2\u01c6\u01dc\7\62\2\2\u01c7\u01dc\7\63\2\2\u01c8"+
		"\u01dc\7\64\2\2\u01c9\u01dc\7\65\2\2\u01ca\u01dc\7\66\2\2\u01cb\u01dc"+
		"\7\67\2\2\u01cc\u01dc\78\2\2\u01cd\u01ce\79\2\2\u01ce\u01cf\7\22\2\2\u01cf"+
		"\u01d0\7\u0083\2\2\u01d0\u01dc\7\24\2\2\u01d1\u01dc\7:\2\2\u01d2\u01dc"+
		"\7;\2\2\u01d3\u01dc\7<\2\2\u01d4\u01dc\7=\2\2\u01d5\u01dc\7>\2\2\u01d6"+
		"\u01dc\7?\2\2\u01d7\u01dc\7@\2\2\u01d8\u01dc\7A\2\2\u01d9\u01dc\7B\2\2"+
		"\u01da\u01dc\7C\2\2\u01db\u01c5\3\2\2\2\u01db\u01c6\3\2\2\2\u01db\u01c7"+
		"\3\2\2\2\u01db\u01c8\3\2\2\2\u01db\u01c9\3\2\2\2\u01db\u01ca\3\2\2\2\u01db"+
		"\u01cb\3\2\2\2\u01db\u01cc\3\2\2\2\u01db\u01cd\3\2\2\2\u01db\u01d1\3\2"+
		"\2\2\u01db\u01d2\3\2\2\2\u01db\u01d3\3\2\2\2\u01db\u01d4\3\2\2\2\u01db"+
		"\u01d5\3\2\2\2\u01db\u01d6\3\2\2\2\u01db\u01d7\3\2\2\2\u01db\u01d8\3\2"+
		"\2\2\u01db\u01d9\3\2\2\2\u01db\u01da\3\2\2\2\u01dcC\3\2\2\2\u01dd\u01e1"+
		"\5d\63\2\u01de\u01e1\7D\2\2\u01df\u01e1\5F$\2\u01e0\u01dd\3\2\2\2\u01e0"+
		"\u01de\3\2\2\2\u01e0\u01df\3\2\2\2\u01e1E\3\2\2\2\u01e2\u01e3\t\7\2\2"+
		"\u01e3G\3\2\2\2\u01e4\u01e5\7\22\2\2\u01e5\u01ea\5@!\2\u01e6\u01e7\7\23"+
		"\2\2\u01e7\u01e9\5@!\2\u01e8\u01e6\3\2\2\2\u01e9\u01ec\3\2\2\2\u01ea\u01e8"+
		"\3\2\2\2\u01ea\u01eb\3\2\2\2\u01eb\u01ed\3\2\2\2\u01ec\u01ea\3\2\2\2\u01ed"+
		"\u01ee\7\24\2\2\u01eeI\3\2\2\2\u01ef\u01f0\b&\1\2\u01f0\u01f1\5D#\2\u01f1"+
		"\u01f2\5H%\2\u01f2\u01f4\7\16\2\2\u01f3\u01f5\5N(\2\u01f4\u01f3\3\2\2"+
		"\2\u01f4\u01f5\3\2\2\2\u01f5\u01f6\3\2\2\2\u01f6\u01f7\7\17\2\2\u01f7"+
		"\u027a\3\2\2\2\u01f8\u01f9\7\16\2\2\u01f9\u01fa\5J&\2\u01fa\u01fb\7\17"+
		"\2\2\u01fb\u027a\3\2\2\2\u01fc\u01fd\7I\2\2\u01fd\u01fe\7\16\2\2\u01fe"+
		"\u0201\5J&\2\u01ff\u0200\7\23\2\2\u0200\u0202\5J&\2\u0201\u01ff\3\2\2"+
		"\2\u0202\u0203\3\2\2\2\u0203\u0201\3\2\2\2\u0203\u0204\3\2\2\2\u0204\u0205"+
		"\3\2\2\2\u0205\u0206\7\17\2\2\u0206\u027a\3\2\2\2\u0207\u027a\5f\64\2"+
		"\u0208\u027a\7\u008a\2\2\u0209\u027a\5B\"\2\u020a\u027a\5\f\7\2\u020b"+
		"\u020c\5@!\2\u020c\u020d\7\7\2\2\u020d\u020e\7\20\2\2\u020e\u027a\3\2"+
		"\2\2\u020f\u0210\5@!\2\u0210\u0212\7\16\2\2\u0211\u0213\5N(\2\u0212\u0211"+
		"\3\2\2\2\u0212\u0213\3\2\2\2\u0213\u0214\3\2\2\2\u0214\u0215\7\17\2\2"+
		"\u0215\u027a\3\2\2\2\u0216\u0217\7J\2\2\u0217\u0218\5@!\2\u0218\u021a"+
		"\7\16\2\2\u0219\u021b\5N(\2\u021a\u0219\3\2\2\2\u021a\u021b\3\2\2\2\u021b"+
		"\u021c\3\2\2\2\u021c\u021d\7\17\2\2\u021d\u027a\3\2\2\2\u021e\u021f\7"+
		"K\2\2\u021f\u027a\5J& \u0220\u0221\7\4\2\2\u0221\u0222\5\36\20\2\u0222"+
		"\u0223\7\5\2\2\u0223\u0224\6&\4\3\u0224\u027a\3\2\2\2\u0225\u0226\7L\2"+
		"\2\u0226\u0227\5J&\2\u0227\u0228\7M\2\2\u0228\u022b\5J&\2\u0229\u022a"+
		"\7N\2\2\u022a\u022c\5J&\2\u022b\u0229\3\2\2\2\u022b\u022c\3\2\2\2\u022c"+
		"\u027a\3\2\2\2\u022d\u022e\7O\2\2\u022e\u022f\5J&\2\u022f\u0231\7P\2\2"+
		"\u0230\u0232\5L\'\2\u0231\u0230\3\2\2\2\u0232\u0233\3\2\2\2\u0233\u0231"+
		"\3\2\2\2\u0233\u0234\3\2\2\2\u0234\u027a\3\2\2\2\u0235\u0236\7Q\2\2\u0236"+
		"\u0237\5J&\2\u0237\u0238\7R\2\2\u0238\u0239\5J&\34\u0239\u027a\3\2\2\2"+
		"\u023a\u023b\7S\2\2\u023b\u023c\5^\60\2\u023c\u023d\7T\2\2\u023d\u023e"+
		"\5J&\2\u023e\u023f\7R\2\2\u023f\u0240\5J&\33\u0240\u027a\3\2\2\2\u0241"+
		"\u0242\5F$\2\u0242\u0244\7\4\2\2\u0243\u0245\5b\62\2\u0244\u0243\3\2\2"+
		"\2\u0244\u0245\3\2\2\2\u0245\u0246\3\2\2\2\u0246\u0247\7\5\2\2\u0247\u027a"+
		"\3\2\2\2\u0248\u0249\5F$\2\u0249\u024a\7\4\2\2\u024a\u024b\5J&\2\u024b"+
		"\u024c\7U\2\2\u024c\u024d\5J&\2\u024d\u024e\7\5\2\2\u024e\u027a\3\2\2"+
		"\2\u024f\u0250\5F$\2\u0250\u0251\7\4\2\2\u0251\u0252\5J&\2\u0252\u0253"+
		"\7V\2\2\u0253\u0254\5X-\2\u0254\u0255\7\u0080\2\2\u0255\u0256\5J&\2\u0256"+
		"\u0257\7\5\2\2\u0257\u027a\3\2\2\2\u0258\u0259\7t\2\2\u0259\u025a\7\16"+
		"\2\2\u025a\u025b\5J&\2\u025b\u025c\7\17\2\2\u025c\u027a\3\2\2\2\u025d"+
		"\u025e\7_\2\2\u025e\u027a\5J&\f\u025f\u0260\7u\2\2\u0260\u027a\5J&\13"+
		"\u0261\u0262\5d\63\2\u0262\u0263\7v\2\2\u0263\u027a\3\2\2\2\u0264\u0265"+
		"\7w\2\2\u0265\u0266\5X-\2\u0266\u0267\7\u0080\2\2\u0267\u0268\5J&\t\u0268"+
		"\u027a\3\2\2\2\u0269\u026a\7x\2\2\u026a\u026b\5X-\2\u026b\u026c\7\u0080"+
		"\2\2\u026c\u026d\5J&\b\u026d\u027a\3\2\2\2\u026e\u026f\5^\60\2\u026f\u0270"+
		"\7.\2\2\u0270\u0271\5J&\7\u0271\u027a\3\2\2\2\u0272\u027a\7y\2\2\u0273"+
		"\u027a\7z\2\2\u0274\u0276\7{\2\2\u0275\u0277\5J&\2\u0276\u0275\3\2\2\2"+
		"\u0276\u0277\3\2\2\2\u0277\u027a\3\2\2\2\u0278\u027a\7|\2\2\u0279\u01ef"+
		"\3\2\2\2\u0279\u01f8\3\2\2\2\u0279\u01fc\3\2\2\2\u0279\u0207\3\2\2\2\u0279"+
		"\u0208\3\2\2\2\u0279\u0209\3\2\2\2\u0279\u020a\3\2\2\2\u0279\u020b\3\2"+
		"\2\2\u0279\u020f\3\2\2\2\u0279\u0216\3\2\2\2\u0279\u021e\3\2\2\2\u0279"+
		"\u0220\3\2\2\2\u0279\u0225\3\2\2\2\u0279\u022d\3\2\2\2\u0279\u0235\3\2"+
		"\2\2\u0279\u023a\3\2\2\2\u0279\u0241\3\2\2\2\u0279\u0248\3\2\2\2\u0279"+
		"\u024f\3\2\2\2\u0279\u0258\3\2\2\2\u0279\u025d\3\2\2\2\u0279\u025f\3\2"+
		"\2\2\u0279\u0261\3\2\2\2\u0279\u0264\3\2\2\2\u0279\u0269\3\2\2\2\u0279"+
		"\u026e\3\2\2\2\u0279\u0272\3\2\2\2\u0279\u0273\3\2\2\2\u0279\u0274\3\2"+
		"\2\2\u0279\u0278\3\2\2\2\u027a\u02ac\3\2\2\2\u027b\u027c\f\25\2\2\u027c"+
		"\u027d\t\b\2\2\u027d\u02ab\5J&\26\u027e\u027f\f\24\2\2\u027f\u0280\t\t"+
		"\2\2\u0280\u02ab\5J&\25\u0281\u0282\f\23\2\2\u0282\u0283\t\n\2\2\u0283"+
		"\u02ab\5J&\24\u0284\u0285\f\22\2\2\u0285\u0286\t\13\2\2\u0286\u02ab\5"+
		"J&\23\u0287\u0288\f\21\2\2\u0288\u0289\7p\2\2\u0289\u02ab\5J&\22\u028a"+
		"\u028b\f\20\2\2\u028b\u028c\7q\2\2\u028c\u02ab\5J&\21\u028d\u028e\f\17"+
		"\2\2\u028e\u028f\t\f\2\2\u028f\u02ab\5J&\20\u0290\u0291\f\16\2\2\u0291"+
		"\u0292\7!\2\2\u0292\u02ab\5J&\17\u0293\u0294\f\'\2\2\u0294\u0295\7\7\2"+
		"\2\u0295\u02ab\7\u0087\2\2\u0296\u0297\f&\2\2\u0297\u0298\7\7\2\2\u0298"+
		"\u02ab\7\u008a\2\2\u0299\u029a\f$\2\2\u029a\u029c\7\16\2\2\u029b\u029d"+
		"\5N(\2\u029c\u029b\3\2\2\2\u029c\u029d\3\2\2\2\u029d\u029e\3\2\2\2\u029e"+
		"\u02ab\7\17\2\2\u029f\u02a0\f!\2\2\u02a0\u02a1\7\22\2\2\u02a1\u02a2\5"+
		"P)\2\u02a2\u02a3\7\24\2\2\u02a3\u02ab\3\2\2\2\u02a4\u02a5\f\27\2\2\u02a5"+
		"\u02a6\7W\2\2\u02a6\u02ab\5@!\2\u02a7\u02a8\f\26\2\2\u02a8\u02a9\7\32"+
		"\2\2\u02a9\u02ab\5@!\2\u02aa\u027b\3\2\2\2\u02aa\u027e\3\2\2\2\u02aa\u0281"+
		"\3\2\2\2\u02aa\u0284\3\2\2\2\u02aa\u0287\3\2\2\2\u02aa\u028a\3\2\2\2\u02aa"+
		"\u028d\3\2\2\2\u02aa\u0290\3\2\2\2\u02aa\u0293\3\2\2\2\u02aa\u0296\3\2"+
		"\2\2\u02aa\u0299\3\2\2\2\u02aa\u029f\3\2\2\2\u02aa\u02a4\3\2\2\2\u02aa"+
		"\u02a7\3\2\2\2\u02ab\u02ae\3\2\2\2\u02ac\u02aa\3\2\2\2\u02ac\u02ad\3\2"+
		"\2\2\u02adK\3\2\2\2\u02ae\u02ac\3\2\2\2\u02af\u02b0\7}\2\2\u02b0\u02b5"+
		"\5^\60\2\u02b1\u02b2\7V\2\2\u02b2\u02b4\5^\60\2\u02b3\u02b1\3\2\2\2\u02b4"+
		"\u02b7\3\2\2\2\u02b5\u02b3\3\2\2\2\u02b5\u02b6\3\2\2\2\u02b6\u02b8\3\2"+
		"\2\2\u02b7\u02b5\3\2\2\2\u02b8\u02b9\7r\2\2\u02b9\u02ba\5J&\2\u02baM\3"+
		"\2\2\2\u02bb\u02be\5P)\2\u02bc\u02be\5R*\2\u02bd\u02bb\3\2\2\2\u02bd\u02bc"+
		"\3\2\2\2\u02beO\3\2\2\2\u02bf\u02c8\5J&\2\u02c0\u02c4\7\23\2\2\u02c1\u02c2"+
		"\7\24\2\2\u02c2\u02c4\7\22\2\2\u02c3\u02c0\3\2\2\2\u02c3\u02c1\3\2\2\2"+
		"\u02c4\u02c5\3\2\2\2\u02c5\u02c7\5J&\2\u02c6\u02c3\3\2\2\2\u02c7\u02ca"+
		"\3\2\2\2\u02c8\u02c6\3\2\2\2\u02c8\u02c9\3\2\2\2\u02c9Q\3\2\2\2\u02ca"+
		"\u02c8\3\2\2\2\u02cb\u02d0\5T+\2\u02cc\u02cd\7\23\2\2\u02cd\u02cf\5T+"+
		"\2\u02ce\u02cc\3\2\2\2\u02cf\u02d2\3\2\2\2\u02d0\u02ce\3\2\2\2\u02d0\u02d1"+
		"\3\2\2\2\u02d1S\3\2\2\2\u02d2\u02d0\3\2\2\2\u02d3\u02d4\7\u008a\2\2\u02d4"+
		"\u02d5\7\31\2\2\u02d5\u02d6\5J&\2\u02d6U\3\2\2\2\u02d7\u02da\5J&\2\u02d8"+
		"\u02da\5@!\2\u02d9\u02d7\3\2\2\2\u02d9\u02d8\3\2\2\2\u02daW\3\2\2\2\u02db"+
		"\u02e0\5Z.\2\u02dc\u02dd\7\23\2\2\u02dd\u02df\5Z.\2\u02de\u02dc\3\2\2"+
		"\2\u02df\u02e2\3\2\2\2\u02e0\u02de\3\2\2\2\u02e0\u02e1\3\2\2\2\u02e1Y"+
		"\3\2\2\2\u02e2\u02e0\3\2\2\2\u02e3\u02e4\5\\/\2\u02e4\u02e5\7\f\2\2\u02e5"+
		"\u02e6\5V,\2\u02e6[\3\2\2\2\u02e7\u02ec\5^\60\2\u02e8\u02e9\7\23\2\2\u02e9"+
		"\u02eb\5^\60\2\u02ea\u02e8\3\2\2\2\u02eb\u02ee\3\2\2\2\u02ec\u02ea\3\2"+
		"\2\2\u02ec\u02ed\3\2\2\2\u02ed]\3\2\2\2\u02ee\u02ec\3\2\2\2\u02ef\u02f0"+
		"\b\60\1\2\u02f0\u02fe\5f\64\2\u02f1\u02fe\7~\2\2\u02f2\u02fe\7\u008a\2"+
		"\2\u02f3\u02f4\7\16\2\2\u02f4\u02f7\5^\60\2\u02f5\u02f6\7\23\2\2\u02f6"+
		"\u02f8\5^\60\2\u02f7\u02f5\3\2\2\2\u02f8\u02f9\3\2\2\2\u02f9\u02f7\3\2"+
		"\2\2\u02f9\u02fa\3\2\2\2\u02fa\u02fb\3\2\2\2\u02fb\u02fc\7\17\2\2\u02fc"+
		"\u02fe\3\2\2\2\u02fd\u02ef\3\2\2\2\u02fd\u02f1\3\2\2\2\u02fd\u02f2\3\2"+
		"\2\2\u02fd\u02f3\3\2\2\2\u02fe\u0304\3\2\2\2\u02ff\u0300\f\3\2\2\u0300"+
		"\u0301\7\f\2\2\u0301\u0303\5@!\2\u0302\u02ff\3\2\2\2\u0303\u0306\3\2\2"+
		"\2\u0304\u0302\3\2\2\2\u0304\u0305\3\2\2\2\u0305_\3\2\2\2\u0306\u0304"+
		"\3\2\2\2\u0307\u030c\7\u008a\2\2\u0308\u0309\7\23\2\2\u0309\u030b\7\u008a"+
		"\2\2\u030a\u0308\3\2\2\2\u030b\u030e\3\2\2\2\u030c\u030a\3\2\2\2\u030c"+
		"\u030d\3\2\2\2\u030da\3\2\2\2\u030e\u030c\3\2\2\2\u030f\u0314\5J&\2\u0310"+
		"\u0311\7\23\2\2\u0311\u0313\5J&\2\u0312\u0310\3\2\2\2\u0313\u0316\3\2"+
		"\2\2\u0314\u0312\3\2\2\2\u0314\u0315\3\2\2\2\u0315c\3\2\2\2\u0316\u0314"+
		"\3\2\2\2\u0317\u031c\7\u008a\2\2\u0318\u0319\7\7\2\2\u0319\u031b\7\u008a"+
		"\2\2\u031a\u0318\3\2\2\2\u031b\u031e\3\2\2\2\u031c\u031a\3\2\2\2\u031c"+
		"\u031d\3\2\2\2\u031de\3\2\2\2\u031e\u031c\3\2\2\2\u031f\u0320\t\r\2\2"+
		"\u0320g\3\2\2\2Wnu\u0081\u0085\u008a\u0099\u009e\u00a5\u00a9\u00ad\u00b1"+
		"\u00b4\u00ba\u00c2\u00ca\u00d1\u00da\u00e0\u00e6\u00f5\u00fd\u0101\u010a"+
		"\u0110\u0116\u011b\u011f\u0124\u0129\u012c\u0130\u0135\u013c\u0140\u0145"+
		"\u014a\u014e\u0155\u0159\u015c\u0160\u0165\u016c\u0173\u017e\u0183\u018a"+
		"\u018d\u0193\u0199\u019f\u01a6\u01b4\u01be\u01c0\u01c2\u01db\u01e0\u01ea"+
		"\u01f4\u0203\u0212\u021a\u022b\u0233\u0244\u0276\u0279\u029c\u02aa\u02ac"+
		"\u02b5\u02bd\u02c3\u02c8\u02d0\u02d9\u02e0\u02ec\u02f9\u02fd\u0304\u030c"+
		"\u0314\u031c";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}