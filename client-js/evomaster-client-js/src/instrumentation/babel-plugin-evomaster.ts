import {NodePath, Visitor} from "@babel/traverse";
import * as BabelTypes from "@babel/types";
import {
    BinaryExpression, CallExpression,
    IfStatement,
    LogicalExpression, Program,
    ReturnStatement,
    Statement,
    UnaryExpression,
    ConditionalExpression, Expression
} from "@babel/types";
import template from "@babel/template";
import InjectedFunctions from "./InjectedFunctions";
import ObjectiveNaming from "./ObjectiveNaming";

/*
    https://github.com/jamiebuilds/babel-handbook
 */

export interface PluginOptions {
    opts?: {
        target?: string;
        runtime?: string;
    };
    file: {
        path: NodePath;
    };
}

export interface Babel {
    types: typeof BabelTypes;
}


const ref = "__EM__";


export default function evomasterPlugin(
    babel: Babel,
): {visitor: Visitor<PluginOptions>} {

    const t = babel.types;

    let statementCounter = 0;
    let branchCounter = 0;
    const objectives = Array<string>();

    let fileName = "filename";

    function addBlockIfNeeded(path: NodePath){

        if(!t.isFor(path.node) && !t.isWhile(path.node)){
            throw Error("Node is not a For: " + path.node);
        }

        const stmt = path.node;

        if(stmt.body && !t.isBlockStatement(stmt.body)){
            stmt.body = t.blockStatement([stmt.body]);
            path.replaceWith(stmt);
        }
    }

    function addBlocksToIf(path: NodePath){

        if(!t.isIfStatement(path.node)){
            throw Error("Node is not a IfStatement: " + path.node);
        }

        const ifs = path.node as IfStatement;

        if(ifs.consequent && !t.isBlockStatement(ifs.consequent)){
            ifs.consequent = t.blockStatement([ifs.consequent]);
            path.replaceWith(ifs);
        }

        if(ifs.alternate && !t.isBlockStatement(ifs.alternate)){
            ifs.alternate = t.blockStatement([ifs.alternate]);
            path.replaceWith(ifs);
        }
    }

    function  replaceUnaryExpression(path: NodePath){

        if(!t.isUnaryExpression(path.node)){
            throw Error("Node is not a UnaryExpression: " + path.node);
        }

        const exp = path.node as UnaryExpression;

        if(exp.operator !== "!"){
            //only handling negation, for now at least...
            return;
        }

        const call = t.callExpression(
            t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.not.name)),
            [exp.argument]
        );

        path.replaceWith(call);
    }



    function replaceLogicalExpression(path: NodePath){

        if(!t.isLogicalExpression(path.node)){
            throw Error("Node is not a LogicalExpression: " + path.node);
        }

        const exp = path.node as LogicalExpression;

        if(exp.operator !== "&&" && exp.operator !== "||"){
            //nothing to do. Note the existence of the "??" operator
            return;
        }

        /*
           TODO: need better, more explicit way to skip traversing
           new nodes we are adding
      */
        if(! exp.loc){
            return;
        }

        const l = exp.loc.start.line;

        const methodName = exp.operator === "&&" ? InjectedFunctions.and.name : InjectedFunctions.or.name;

        /*
            TODO: there is proper documentation on this function.
            Look like is checking for some types, which in theory should always be pure, like literals.
            But very unclear on its features... eg, would it handle as pure "!false" ???
            TODO need to check... furthermore we do not care if throwing exception
         */
         // const pure = t.isPureish(exp.right);
        const pure = isPureExpression(exp.right);

        const left = t.arrowFunctionExpression([], exp.left, false);
        const right = t.arrowFunctionExpression([], exp.right, false);

        const call = t.callExpression(
            t.memberExpression(t.identifier(ref), t.identifier(methodName)),
            [left,  right, t.booleanLiteral(pure),
                t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(branchCounter)]
        );

        objectives.push(ObjectiveNaming.branchObjectiveName(fileName, l, branchCounter, true));
        objectives.push(ObjectiveNaming.branchObjectiveName(fileName, l, branchCounter, false));

        path.replaceWith(call);
        branchCounter++;
    }

    /**
     * here, we analyze whether the expression in LogicalExpression (from right to left) is pure,
     *      i.e., the expression can be evaluated without any possible exception
     *
     * see all available expression with https://babeljs.io/docs/en/babel-types#expression
     *
     */
    function isPureExpression(node: Expression): boolean{

        let pure = false;

        if (t.isStringLiteral(node)
            || t.isNumericLiteral(node) || t.isNullLiteral(node) || t.isBooleanLiteral(node) || t.isBigIntLiteral(node)
            || t.isRegExpLiteral(node)
            // || t.isDecimalLiteral(node) //TODO do not find this lib, but it exists https://babeljs.io/docs/en/babel-types#decimalliteral
            || t.isAssignmentExpression(node)
            || t.isArrayExpression(node)
            || t.isClassExpression(node)
            || t.isObjectExpression(node) // need a check
            || t.isImport(node) || t.isJSXElement(node) || t.isJSXFragment(node) // need to discuss
        )
            pure = true
        else if (t.isParenthesizedExpression(node)){
            pure = isPureExpression(node.expression)
        }else if (t.isSequenceExpression(node)){
            for (let exp of node.expressions){
                pure = pure && isPureExpression(exp)
            }
        } else if (t.isTemplateLiteral(node)){
            // https://babeljs.io/docs/en/babel-types#templateliteral
            for (let exp of node.expressions){
                pure = pure && isPureExpression(exp)
            }
        } else if (t.isTaggedTemplateExpression(node)){
            // https://babeljs.io/docs/en/babel-types#taggedtemplateexpression
            pure = isPureExpression(node.tag)  && isPureExpression(node.quasi)
        } else if (t.isUnaryExpression(node)){
            /*
                https://babeljs.io/docs/en/babel-types#unaryexpression
                "void" | "throw" | "delete" | "!" | "+" | "-" | "~" | "typeof"
             */

            const excludeOp= ["throw", "delete"] // Man: not sure whether to include "void"
            pure = !excludeOp.includes(node.operator) && isPureExpression(node.argument)

        } else if (t.isUpdateExpression(node)){
            // https://babeljs.io/docs/en/babel-types#updateexpression
            pure = isPureExpression(node.argument)
        } else if (t.isBinaryExpression(node)){
            // https://babeljs.io/docs/en/babel-types#binaryexpression
            pure = isPureExpression(node.right) && isPureExpression(node.left)
        }
        else if (t.isArrowFunctionExpression(node)
            || t.isAwaitExpression(node) // await expression which might lead to some expression
            || t.isCallExpression(node)
            || t.isThisExpression(node)
            // || t.isModuleExpression(node) //TODO do not find this lib, but it exists https://babeljs.io/docs/en/babel-types#moduleexpression
            || t.isConditionalExpression(node) // TODO https://babeljs.io/docs/en/babel-types#conditionalexpression
            || t.isDoExpression(node) //TODO https://babeljs.io/docs/en/babel-types#doexpression
            || t.isFunctionExpression(node) // TODO check if it is supertype https://babeljs.io/docs/en/babel-types#functionexpression
            || t.isIdentifier(node) // TODO unsure https://babeljs.io/docs/en/babel-types#identifier
            || t.isLogicalExpression(node) // TODO need to check whether it should be here
            || t.isMemberExpression(node) // TODO need to check
            || t.isMetaProperty(node) || t.isNewExpression(node) // need to discuss, it might not be invoked unless it satisfies the condition as specified
            || t.isOptionalCallExpression(node) || t.isOptionalMemberExpression(node) // TODO need to check https://babeljs.io/docs/en/babel-types#optionalcallexpression
            || t.isPipelinePrimaryTopicReference(node) //TODO need a further check
            // || t.isRecordExpression(node) // TODO do not find it in this lib
            // || t.isTupleExpression(node) // TODO do not find it in this lib
            || t.isSuper(node) // need a further check
            || t.isTSAsExpression(node) || t.isTSNonNullExpression(node) || t.isTSTypeAssertion(node) // TODO
            || t.isTypeCastExpression(node) //TODO
            || t.isYieldExpression(node) // TODO need a further check, e.g., whether executing such experssion will lead to any side-effect

        ){
            pure = false
        }else{
            throw Error("Missing expression type in the pure analysis: " + node.type);
        }
        return pure;

    }

    function replaceBinaryExpression(path: NodePath){

        if(!t.isBinaryExpression(path.node)){
            throw Error("Node is not a BinaryExpression: " + path.node);
        }

        const exp = path.node as BinaryExpression;

        const validOps = ["==", "===", "!=", "!==", "<", "<=", ">", ">="];

        if(! validOps.includes(exp.operator)){
            //nothing to do
            return;
        }

        /*
             TODO: need better, more explicit way to skip traversing
             new nodes we are adding
        */
        if(! exp.loc){
            return;
        }

        const l = exp.loc.start.line;

        const call = t.callExpression(
            t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.cmp.name)),
            [exp.left, t.stringLiteral(exp.operator), exp.right,
                t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(branchCounter)]
        );

        objectives.push(ObjectiveNaming.branchObjectiveName(fileName, l, branchCounter, true));
        objectives.push(ObjectiveNaming.branchObjectiveName(fileName, l, branchCounter, false));

        path.replaceWith(call);
        branchCounter++;
    }

    function replaceConditionalExpression(path: NodePath){

        if(!t.isConditionalExpression(path.node)){
            throw Error("Node is not a ConditionalExpression: " + path.node);
        }
        const exp = path.node as ConditionalExpression;

        if(! exp.loc){
            return;
        }

        const l = exp.loc.start.line;
        /*
            test ? consequent : alternate
            test: Expression;
            consequent: Expression;
            alternate: Expression;

            transformed code:
                test? __EM__.ternary(()=>consequent, ... ) : __EM__.ternary(()=>alternate, ...)

            here, we create additional two statements targets for 'consequent' and 'alternate'
            for the statement targets,
                if consequent(/alternate) is executed without exception, h is 1
                otherwise h is 0.5

            Note that we do not further replace 'test' here.
            if it is related to condition, it will be replaced by other existing replacement and
            additional branch will be added there.

         */
        const consequent = t.arrowFunctionExpression([], exp.consequent, false);
        const alternate = t.arrowFunctionExpression([], exp.alternate, false);


        objectives.push(ObjectiveNaming.statementObjectiveName(fileName, l, statementCounter));
        exp.consequent = t.callExpression(
            t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.ternary.name)),
            [consequent,
                t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(statementCounter)]
        );
        statementCounter++;

        objectives.push(ObjectiveNaming.statementObjectiveName(fileName, l, statementCounter));
        exp.alternate = t.callExpression(
            t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.ternary.name)),
            [alternate,
                t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(statementCounter)]
        );
        statementCounter++;
    }

    function replaceCallExpression(path: NodePath){

        //if(! t.isExpr) //TODO there is no available check for call expressions???

        const call = path.node as CallExpression;

        /*
            TODO: need better, more explicit way to skip traversing
            new nodes we are adding
        */
        if(! call.loc ||
            // @ts-ignore
            call.evomaster
        ){
            return;
        }

        const l = call.loc.start.line;

        let replaced;

        //TODO only for known names

        if(t.isMemberExpression(call.callee)) {
            replaced = t.callExpression(
                t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.callTracked.name)),
                [t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(branchCounter),
                    // @ts-ignore
                    call.callee.object, t.stringLiteral(call.callee.property.name), ...call.arguments]
            );
            branchCounter++;
        } else {
            replaced = t.callExpression(
                t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.callBase.name)),
                [t.arrowFunctionExpression([], call, false) ]
            );
            // @ts-ignore
            call.evomaster = true;
        }

        path.replaceWith(replaced);
    }

    function addLineProbeIfNeeded(path: NodePath){

        if(! t.isStatement(path.node)){
            throw Error("Node is not a Statement: " + path.node);
        }

        const stmt = path.node as Statement;

        if(t.isBlockStatement(stmt)){
            //no point in instrumenting it. Recall, we still instrument its content anyway
            return;
        }

        /*
            TODO: need better, more explicit way to skip traversing
            new nodes we are adding
         */
        if(! stmt.loc){
            return;
        }

        const l = stmt.loc.start.line;

        objectives.push(ObjectiveNaming.lineObjectiveName(fileName,l));
        objectives.push(ObjectiveNaming.statementObjectiveName(fileName, l, statementCounter));

        if( (t.isReturnStatement(stmt) && !stmt.argument)
            || t.isContinueStatement(stmt) //FIXME: did i forget break? or was it included here?
            || t.isThrowStatement(stmt)
            /*
                The following are tricky. They might have inside return stmts
                or labeled jumps (continue/break) to outer-loops.
             */
            || t.isFor(stmt)
            || t.isWhile(stmt)
            || t.isIfStatement(stmt)
            || t.isTryStatement(stmt)
        ){

            const mark = template.ast(
                `${ref}.${InjectedFunctions.markStatementForCompletion.name}("${fileName}",${l},${statementCounter})`);
            path.insertBefore(mark);

        } else {

            const enter = template.ast(
                `${ref}.${InjectedFunctions.enteringStatement.name}("${fileName}",${l},${statementCounter})`);
            path.insertBefore(enter);

            if (t.isReturnStatement(stmt)) {

                const rs = stmt as ReturnStatement;
                const call = t.callExpression(
                    t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.completingStatement.name)),
                    [rs.argument, t.stringLiteral(fileName), t.numericLiteral(l), t.numericLiteral(statementCounter)]
                );

                path.replaceWith(t.returnStatement(call));

            } else {

                const completed = template.ast(
                    `${ref}.${InjectedFunctions.completedStatement.name}("${fileName}",${l},${statementCounter})`);
                path.insertAfter(completed);
            }
        }

        statementCounter++;
    }


    return {
        visitor: {
            // File: {
            //   enter(path){
            //       //FIXME does not seem this is actually reached in the tests
            //       statementCounter = 0;
            //   }
            // },
            Program: {
                enter(path: NodePath, state) {
                    t.addComment(path.node, "leading", "File instrumented with EvoMaster", true);

                    statementCounter = 0;
                    branchCounter = 0;
                    objectives.length = 0;

                    //@ts-ignore
                    const srcFilePath: string = state.file.opts.filename;
                    //@ts-ignore
                    const root: string = state.file.opts.root;

                    fileName = srcFilePath.substr(root.length, srcFilePath.length);
                    if(fileName.startsWith('/') || fileName.startsWith('\\')){
                        fileName = fileName.substr(1, fileName.length);
                    }
                    fileName = fileName.replace(/\\/g, "/");

                    const emImport = template.ast(
                        "const "+ref+" = require(\"evomaster-client-js\").InjectedFunctions;"
                    );

                    //@ts-ignore
                    path.unshiftContainer('body', emImport);

                    objectives.push(ObjectiveNaming.fileObjectiveName(fileName));

                },
                exit(path: NodePath) {
                    //once the whole program is instrumented, the content of "objectives" array will be ready to be injected

                    const unique = Array.from(new Set<string>(objectives)).sort();

                    const call = t.callExpression(
                        t.memberExpression(t.identifier(ref), t.identifier(InjectedFunctions.registerTargets.name)),
                        [t.arrayExpression(unique.map(e => t.stringLiteral(e)))]
                    );
                    const stmt = t.expressionStatement(call);

                    const p = path.node as Program;
                    p.body.splice(1, 0, stmt);
                    path.replaceWith(p);
                }
            },
            BinaryExpression:{
                enter(path: NodePath){
                    replaceBinaryExpression(path);
                }
            },
            LogicalExpression:{
                enter(path: NodePath){
                    replaceLogicalExpression(path);
                }
            },
            UnaryExpression:{
                enter(path: NodePath){
                    replaceUnaryExpression(path);
                }
            },
            ConditionalExpression:{
                enter(path: NodePath){
                    replaceConditionalExpression(path);
                }
            },
            Statement: {
                enter(path: NodePath){

                    if(t.isIfStatement(path.node)){
                        addBlocksToIf(path);
                    }

                    if(t.isFor(path.node) || t.isWhile(path.node)){
                        addBlockIfNeeded(path);
                    }

                    addLineProbeIfNeeded(path);
                }
            },
            CallExpression:{
                enter(path: NodePath){
                    replaceCallExpression(path);
                }
            }
        }
    };
}

