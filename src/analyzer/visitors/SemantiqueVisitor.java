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

        HashMap<String, VarType> table = (HashMap<String, VarType>) data;

        if (table.containsKey(varName)) {
            throw new SemantiqueError(String.format("Identifier %s has multiple declarations", varName));
        }

        VarType declaredType = stringToVarType(node.getValue());
        table.put(varName, declaredType);
        this.VAR++;

        if (node.jjtGetNumChildren() > 1) {
            VarType exprType = (VarType) node.jjtGetChild(1).jjtAccept(this, table);
            if (!typesEqual(declaredType, exprType)) {
                throw new SemantiqueError(
                        String.format("Invalid type in assignation of Identifier %s", varName)
                );
            }
        }

        return null;
    }

    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        HashMap<String, VarType> table = (HashMap<String, VarType>) data;

        ASTIdentifier idNode = (ASTIdentifier) node.jjtGetChild(0);
        String varName = idNode.getValue();

        if (!table.containsKey(varName)) {
            throw new SemantiqueError(String.format("Variable %s was not declared", varName));
        }

        VarType varType = table.get(varName);
        VarType exprType = (VarType) node.jjtGetChild(1).jjtAccept(this, table);

        if (!typesEqual(varType, exprType)) {
            throw new SemantiqueError(
                    String.format("Invalid type in assignation of Identifier %s", varName)
            );
        }
        return null;
    }

    // les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    // On doit aussi compter les conditions dans les variables IF et WHILE
    // Elle sont aussi les seules structure avec des block qui devront garder leur déclaration locale.
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        this.IF++;

        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIfCond node, Object data) {
        if (node.jjtGetNumChildren() > 0) {
            VarType condType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
            if (condType != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in condition");
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTIfBlock node, Object data) {
        HashMap<String, VarType> parent = (HashMap<String, VarType>) data;
        HashMap<String, VarType> local = new HashMap<>(parent);
        node.childrenAccept(this, local);
        return null;
    }

    @Override
    public Object visit(ASTElseBlock node, Object data) {
        HashMap<String, VarType> parent = (HashMap<String, VarType>) data;
        HashMap<String, VarType> local = new HashMap<>(parent);
        node.childrenAccept(this, local);
        return null;
    }

    @Override
    public Object visit(ASTTernary node, Object data) {
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        if (nbChildren == 3) {
            this.IF++;

            VarType condType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
            if (condType != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in condition");
            }

            VarType thenType = (VarType) node.jjtGetChild(1).jjtAccept(this, data);
            VarType elseType = (VarType) node.jjtGetChild(2).jjtAccept(this, data);

            if (!typesEqual(thenType, elseType)) {
                throw new SemantiqueError("Invalid type in expression");
            }

            return thenType;
        }

        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        this.WHILE++;

        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTWhileCond node, Object data) {
        if (node.jjtGetNumChildren() > 0) {
            VarType condType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
            if (condType != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in condition");
            }
        }
        return null;
    }

    @Override
    public Object visit(ASTWhileBlock node, Object data) {
        HashMap<String, VarType> parent = (HashMap<String, VarType>) data;
        HashMap<String, VarType> local = new HashMap<>(parent);
        node.childrenAccept(this, local);
        return null;
    }

    @Override
    public Object visit(ASTDoWhileStmt node, Object data) {
        this.WHILE++;

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
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren == 1) {
            return node.jjtGetChild(0).jjtAccept(this, data);
        }

        this.OP += (nbChildren - 1);

        String op = node.getValue();
        boolean isNumericCompare = "<".equals(op) || "<=".equals(op)
                || ">".equals(op) || ">=".equals(op);

        VarType leftType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);

        for (int i = 1; i < nbChildren; i++) {
            VarType rightType = (VarType) node.jjtGetChild(i).jjtAccept(this, data);

            if (isNumericCompare) {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    throw new SemantiqueError("Invalid type in expression");
                }
            } else {
                if (!typesEqual(leftType, rightType)) {
                    throw new SemantiqueError("Invalid type in expression");
                }
            }

            leftType = rightType;
        }

        return VarType.BOOL;
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
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren > 1) {
            this.OP += (nbChildren - 1);
        }

        for (int i = 0; i < nbChildren; i++) {
            VarType childType = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (childType != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTAddExpr node, Object data) {
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren > 1) {
            this.OP += (nbChildren - 1);
        }

        VarType resultType = null;
        for (int i = 0; i < nbChildren; i++) {
            VarType childType = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (!isNumeric(childType)) {
                throw new SemantiqueError("Invalid type in expression");
            }

            if (resultType == null) {
                resultType = childType;
            } else if (resultType != childType) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        return resultType;
    }

    @Override
    public Object visit(ASTMultExpr node, Object data) {
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren > 1) {
            this.OP += (nbChildren - 1);
        }

        VarType resultType = null;
        for (int i = 0; i < nbChildren; i++) {
            VarType childType = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (!isNumeric(childType)) {
                throw new SemantiqueError("Invalid type in expression");
            }

            if (resultType == null) {
                resultType = childType;
            } else if (resultType != childType) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        return resultType;
    }

    /*
        Opérateur unaire
        Les opérateurs unaires ont toujours un seul enfant.
    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        this.OP++;

        if (node.jjtGetNumChildren() > 0) {
            VarType childType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
            if (childType != VarType.BOOL) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTNegExpr node, Object data) {
        this.OP++;

        if (node.jjtGetNumChildren() > 0) {
            VarType childType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);
            if (!isNumeric(childType)) {
                throw new SemantiqueError("Invalid type in expression");
            }
            return childType;
        }

        return null;
    }

    /*
        Les noeud ASTIdentifier ayant comme parent "GenValue" doivent vérifier leur type.
        On peut envoyer une information à un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        if (node.jjtGetNumChildren() == 0) {
            return null;
        }
        return node.jjtGetChild(0).jjtAccept(this, data);
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        if (node.jjtGetParent() instanceof ASTGenValue) {
            HashMap<String, VarType> table = (HashMap<String, VarType>) data;
            String name = node.getValue();

            if (!table.containsKey(name)) {
                throw new SemantiqueError(String.format("Variable %s was not declared", name));
            }

            return table.get(name);
        }

        return null;
    }

    @Override
    public Object visit(ASTBoolValue node, Object data) {
        return VarType.BOOL;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        return VarType.INT;
    }

    @Override
    public Object visit(ASTRealValue node, Object data) {
        return VarType.FLOAT;
    }

    @Override
    public Object visit(ASTListExpr node, Object data) {
        int nbChildren = node.jjtGetNumChildren();

        if (nbChildren == 0) {
            return VarType.LIST;
        }

        VarType firstType = (VarType) node.jjtGetChild(0).jjtAccept(this, data);

        for (int i = 1; i < nbChildren; i++) {
            VarType current = (VarType) node.jjtGetChild(i).jjtAccept(this, data);
            if (!typesEqual(firstType, current)) {
                throw new SemantiqueError("Invalid type in expression");
            }
        }

        return VarType.LIST;
    }


    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        INT,
        FLOAT,
        BOOL,
        LIST
    }

    private VarType stringToVarType(String typeStr) {
        if (typeStr == null) return null;

        switch (typeStr) {
            case "int":
                return VarType.INT;
            case "float":
                return VarType.FLOAT;
            case "bool":
                return VarType.BOOL;
            case "list":
                return VarType.LIST;
            default:
                throw new SemantiqueError("Invalid use of undefined Identifier " + typeStr);
        }
    }

    private boolean isNumeric(VarType t) {
        return t == VarType.INT || t == VarType.FLOAT;
    }

    private boolean typesEqual(VarType a, VarType b) {
        return a == b;
    }
}