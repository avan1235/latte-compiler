grammar Latte;

program
    : topDef+
    ;

topDef
    : type ID '(' arg? ')' block                    # Fun
    | classDef                                      # Class
    ;

classDef
    : 'class' ID '{' classBodyDef+ '}'              # ClassNotExtendingDef
    | 'class' ID 'extends' ID '{' classBodyDef+ '}' # ClassExtendingDef
    ;

classBodyDef
    : type ID ';'                   # ClassField
    | type ID '(' arg? ')' block    # ClassMethod
    ;

arg
    : type ID ( ',' type ID )*
    ;

block
    : '{' stmt* '}'
    ;

stmt
    : ';'                                # Empty
    | block                              # BlockStmt
    | type item ( ',' item )* ';'        # Decl
    | expr '.' ID '=' expr ';'           # RefAss
    | ID '=' expr ';'                    # Ass
    | expr '.' ID '++' ';'               # RefIncr
    | ID '++' ';'                        # Incr
    | expr '.' ID '--' ';'               # RefDecr
    | ID '--' ';'                        # Decr
    | 'return' expr ';'                  # Ret
    | 'return' ';'                       # VRet
    | 'if' '(' expr ')' stmt             # Cond
    | 'if' '(' expr ')' stmt 'else' stmt # CondElse
    | 'while' '(' expr ')' stmt          # While
    | expr ';'                           # SExp
    ;

type
    : 'int'     # Int
    | 'string'  # Str
    | 'boolean' # Bool
    | 'void'    # Void
    | ID        # ClassType
    ;

item
    : ID
    | ID '=' expr
    ;

expr
    : expr '.' ID '(' ( expr ( ',' expr )* )? ')'  # EClassMethodCall
    | expr '.' ID                                  # EClassField
    | unOp expr                                    # EUnOp
    | expr mulOp expr                              # EMulOp
    | expr addOp expr                              # EAddOp
    | expr relOp expr                              # ERelOp
    | <assoc=right> expr '&&' expr                 # EAnd
    | <assoc=right> expr '||' expr                 # EOr
    | 'new' type                                   # EClassConstructorCall
    | 'null'                                       # ENull
    | 'self'                                       # EThis
    | 'true'                                       # ETrue
    | 'false'                                      # EFalse
    | '(' type ')' expr                            # ECast
    | '(' expr ')'                                 # EParen
    | ID '(' ( expr ( ',' expr )* )? ')'           # EFunCall
    | STR                                          # EStr
    | INT                                          # EInt
    | ID                                           # EId
    ;

addOp
    : '+'                           # Plus
    | '-'                           # Minus
    ;

mulOp
    : '*'                           # Times
    | '/'                           # Divide
    | '%'                           # Mod
    ;

relOp
    : '<'                           # LT
    | '<='                          # LE
    | '>'                           # GT
    | '>='                          # GE
    | '=='                          # EQ
    | '!='                          # NE
    ;

unOp
    : '!'                          # Not
    | '-'                          # Neg
    ;

COMMENT : ('#' ~[\r\n]* | '//' ~[\r\n]*) -> channel(HIDDEN);
MULTICOMMENT : '/*' .*? '*/' -> channel(HIDDEN);

fragment Letter  : Capital | Small ;
fragment Capital : [A-Z\u00C0-\u00D6\u00D8-\u00DE] ;
fragment Small   : [a-z\u00DF-\u00F6\u00F8-\u00FF] ;
fragment Digit : [0-9] ;

INT : Digit+ ;
fragment ID_First : Letter | '_';
ID : ID_First (ID_First | Digit)* ;

WS : (' ' | '\r' | '\t' | '\n')+ ->  skip;

STR
    :   '"' StringCharacters? '"'
    ;
fragment StringCharacters
    :   StringCharacter+
    ;
fragment
StringCharacter
    :   ~["\\]
    |   '\\' [tnr"\\]
    ;
