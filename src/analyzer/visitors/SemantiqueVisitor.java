package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import java.io.PrintWriter;
import java.util.*;

/**
 * Created: 19-01-10
 * Last Changed: 01-10-25
 * Author: Félix Brunet, Raphael Tremblay
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreurs lorsqu'une erreur sémantique est détectée.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter m_writer;

    private HashMap<String, VarType> SymbolTable = new HashMap<>(); // mapping variable -> type


    // variable pour les metrics
    public int VAR = 0;
    public int WHILE = 0;
    public int IF = 0;
    public int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        m_writer = writer;
    }

    /*
        IMPORTANT:
        *
        * L'implémentation des visiteurs se base sur la grammaire fournie (Grammaire.jjt). Il faut donc la consulter pour
        * déterminer les noeuds enfants à visiter. Cela vous sera utile pour lancer les erreurs au bon moment.
        * Pour chaque noeud, on peut :
        *   1. Déterminer le nombre d'enfants d'un noeud : jjtGetNumChildren()
        *   2. Visiter tous les noeuds enfants: childrenAccept()
        *   3. Accéder à un noeud enfant : jjtGetChild()
        *   4. Visiter un noeud enfant : jjtAccept()
        *   5. Accéder à m_value (type) ou m_ops (vecteur des opérateurs) selon la classe de noeud AST (src/analyser/ast)
        *
        * Cela permet d'analyser l'intégralité de l'arbre de syntaxe abstraite (AST) et d'effectuer une analyse sémantique du code.
        *
        * Le Visiteur doit lancer des erreurs lorsqu'une situation arrive.
        *
        * Pour vous aider, voici le code à utiliser pour lancer les erreurs :
        *
        * - Utilisation d'identifiant non défini :
        *   throw new SemantiqueError("Invalid use of undefined Identifier " + node.getValue());
        *
        * - Utilisation d'une variable non déclarée :
        *   throw new SemantiqueError(String.format("Variable %s was not declared", varName));
        *
        * - Plusieurs déclarations pour un identifiant. Ex : int a = 1; bool a = true; :
        *   throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
        *
        * - Utilisation d'un type numérique dans la condition d'un if ou d'un while :
        *   throw new SemantiqueError("Invalid type in condition");
        *
        * - Utilisation de types non valides pour des opérations de comparaison :
        *   throw new SemantiqueError("Invalid type in expression");
        *
        * - Assignation d'une valeur à une variable qui a déjà reçu une valeur d'un autre type. Ex : a = 1; a = true; :
        *   throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s", varName));
        *
        * - Les éléments d'une liste doivent être du même type. Ex : [1, 2, true] :
        *   throw new SemantiqueError("Invalid type in expression");
        * */


    @Override
    public Object visit(SimpleNode node, Object data) {
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, SymbolTable);
        m_writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.OP));
        return null;
    }

    // Déclaration et assignation:
    // On doit vérifier que le type de la variable est compatible avec celui de l'expression.

    @Override
    public Object visit(ASTDeclareStmt node, Object data) {
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        // TODO
        node.childrenAccept(this, data);

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    // les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    // On doit aussi compter les conditions dans les variables IF et WHILE
    // Elle sont aussi les seules structure avec des block qui devront garder leur déclaration locale.
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIfCond node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIfBlock node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTElseBlock node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTTernary node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileCond node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileBlock node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTDoWhileStmt node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*
            Attention, ce noeud est plus complexe que les autres :
            - S’il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.
            - S’il a plus d'un enfant, alors il s'agit d'une comparaison. Il a donc pour type "Bool".
            - Il n'est pas acceptable de faire des comparaisons de booléen avec les opérateurs < > <= >=.
            - Les opérateurs == et != peuvent être utilisé pour les nombres et les booléens, mais il faut que le type
            soit le même des deux côtés de l'égalité/l'inégalité.
        */
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    /*
        Opérateur à opérants multiples :
        - Il peuvent avoir de 2 à infinie noeuds enfants qui doivent tous être du même type que leur noeud parent
        - Par exemple, un AddExpr peux avoir une multiplication et un entier comme enfant, mais ne pourrait pas
        avoir une opération logique comme enfant.
        - Pour cette étapes il est recommandé de rédiger une function qui encapsule la visite des noeuds enfant
        et vérification de type
     */
    @Override
    public Object visit(ASTLogExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTMultExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    /*
        Opérateur unaire
        Les opérateurs unaires ont toujours un seul enfant.
    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTNegExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }

    /*
        Les noeud ASTIdentifier ayant comme parent "GenValue" doivent vérifier leur type.
        On peut envoyer une information à un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (node.jjtGetParent() instanceof ASTGenValue) {
            // TODO
        }

        return null;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        // TODO
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        // TODO
        return null;
    }

    @Override
    public Object visit(ASTRealValue node, Object data) {
        // TODO
        return null;
    }

    @Override
    public Object visit(ASTListExpr node, Object data) {
        // TODO
        node.childrenAccept(this, data);
        return null;
    }


    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        INT
        // À compléter
    }


    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }

    }
}
