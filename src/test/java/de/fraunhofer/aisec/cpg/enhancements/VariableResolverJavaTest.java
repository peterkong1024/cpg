package de.fraunhofer.aisec.cpg.enhancements;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.fraunhofer.aisec.cpg.TranslationConfiguration;
import de.fraunhofer.aisec.cpg.TranslationManager;
import de.fraunhofer.aisec.cpg.graph.CallExpression;
import de.fraunhofer.aisec.cpg.graph.DeclaredReferenceExpression;
import de.fraunhofer.aisec.cpg.graph.Expression;
import de.fraunhofer.aisec.cpg.graph.FieldDeclaration;
import de.fraunhofer.aisec.cpg.graph.ForStatement;
import de.fraunhofer.aisec.cpg.graph.Literal;
import de.fraunhofer.aisec.cpg.graph.MemberExpression;
import de.fraunhofer.aisec.cpg.graph.MethodDeclaration;
import de.fraunhofer.aisec.cpg.graph.Node;
import de.fraunhofer.aisec.cpg.graph.ParamVariableDeclaration;
import de.fraunhofer.aisec.cpg.graph.RecordDeclaration;
import de.fraunhofer.aisec.cpg.graph.TranslationUnitDeclaration;
import de.fraunhofer.aisec.cpg.graph.ValueDeclaration;
import de.fraunhofer.aisec.cpg.graph.VariableDeclaration;
import de.fraunhofer.aisec.cpg.helpers.NodeComparator;
import de.fraunhofer.aisec.cpg.helpers.SubgraphWalker;
import de.fraunhofer.aisec.cpg.helpers.Util;
import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VariableResolverJavaTest {

  // Externally defined static global

  private RecordDeclaration externalClass;
  private FieldDeclaration externVarName;
  private FieldDeclaration externStaticVarName;

  private RecordDeclaration outerClass;
  private FieldDeclaration outerVarName;
  private FieldDeclaration outerStaticVarName;
  private FieldDeclaration outerImpThis;

  private RecordDeclaration innerClass;
  private FieldDeclaration innerVarName;
  private FieldDeclaration innerStaticVarName;
  private FieldDeclaration innerImpThis;
  private FieldDeclaration innerImpOuter;

  private MethodDeclaration main;

  private MethodDeclaration outer_function1;
  private List<ForStatement> forStatements;
  private MethodDeclaration outer_function2;
  private MethodDeclaration outer_function3;
  private MethodDeclaration outer_function4;

  private MethodDeclaration inner_function1;
  private MethodDeclaration inner_function2;
  private MethodDeclaration inner_function3;

  private Map<String, Expression> callParamMap = new HashMap<>();

  @BeforeAll
  public void initTests() throws ExecutionException, InterruptedException {
    final String topLevelPath = "src/test/resources/variables/";
    List<String> fileNames = Arrays.asList("ScopeVariables.java", "ExternalClass.java");
    List<File> fileLocations =
        fileNames.stream()
            .map(fileName -> new File(topLevelPath + fileName))
            .collect(Collectors.toList());
    TranslationConfiguration config =
        TranslationConfiguration.builder()
            .sourceLocations(fileLocations.toArray(new File[fileNames.size()]))
            .topLevel(new File(topLevelPath))
            .defaultPasses()
            .debugParser(true)
            .failOnError(true)
            .build();

    TranslationManager analyzer = TranslationManager.builder().config(config).build();
    List<TranslationUnitDeclaration> tu = analyzer.analyze().get().getTranslationUnits();

    List<Node> nodes =
        tu.stream()
            .flatMap(tUnit -> SubgraphWalker.flattenAST(tUnit).stream())
            .collect(Collectors.toList());
    List<CallExpression> calls =
        Util.filterCast(nodes, CallExpression.class).stream()
            .filter(call -> call.getName().equals("printLog"))
            .collect(Collectors.toList());
    calls.sort(new NodeComparator());

    List<RecordDeclaration> records = Util.filterCast(nodes, RecordDeclaration.class);

    // Extract all Variable declarations and field declarations for matching
    externalClass =
        Util.getOfTypeWithName(nodes, RecordDeclaration.class, "variables.ExternalClass");
    externVarName = Util.getSubnodeOfTypeWithName(externalClass, FieldDeclaration.class, "varName");
    externStaticVarName =
        Util.getSubnodeOfTypeWithName(externalClass, FieldDeclaration.class, "staticVarName");
    outerClass = Util.getOfTypeWithName(nodes, RecordDeclaration.class, "variables.ScopeVariables");
    outerVarName = Util.getSubnodeOfTypeWithName(outerClass, FieldDeclaration.class, "varName");
    outerStaticVarName =
        Util.getSubnodeOfTypeWithName(outerClass, FieldDeclaration.class, "staticVarName");
    outerImpThis = Util.getSubnodeOfTypeWithName(outerClass, FieldDeclaration.class, "this");

    // Inner class and its fields
    innerClass =
        Util.getOfTypeWithName(
            nodes, RecordDeclaration.class, "variables.ScopeVariables.InnerClass");
    innerVarName = Util.getSubnodeOfTypeWithName(innerClass, FieldDeclaration.class, "varName");
    innerStaticVarName =
        Util.getSubnodeOfTypeWithName(innerClass, FieldDeclaration.class, "staticVarName");
    innerImpThis = Util.getSubnodeOfTypeWithName(innerClass, FieldDeclaration.class, "this");
    innerImpOuter =
        Util.getSubnodeOfTypeWithName(innerClass, FieldDeclaration.class, "ScopeVariables.this");

    main = Util.getSubnodeOfTypeWithName(outerClass, MethodDeclaration.class, "main");

    outer_function1 =
        outerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function1"))
            .collect(Collectors.toList())
            .get(0);
    forStatements = Util.filterCast(SubgraphWalker.flattenAST(outer_function1), ForStatement.class);

    // Functions i nthe outer and inner object
    outer_function2 =
        outerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function2"))
            .collect(Collectors.toList())
            .get(0);
    outer_function3 =
        outerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function3"))
            .collect(Collectors.toList())
            .get(0);
    outer_function4 =
        outerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function4"))
            .collect(Collectors.toList())
            .get(0);
    inner_function1 =
        innerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function1"))
            .collect(Collectors.toList())
            .get(0);
    inner_function2 =
        innerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function2"))
            .collect(Collectors.toList())
            .get(0);
    inner_function3 =
        innerClass.getMethods().stream()
            .filter(method -> method.getName().equals("function3"))
            .collect(Collectors.toList())
            .get(0);

    for (CallExpression call : calls) {
      Expression first = call.getArguments().get(0);
      String logId = ((Literal) first).getValue().toString();

      Expression second = call.getArguments().get(1);
      callParamMap.put(logId, second);
    }
  }

  public DeclaredReferenceExpression getCallWithReference(String literal) {
    Expression exp = callParamMap.get(literal);
    if (exp instanceof DeclaredReferenceExpression) return (DeclaredReferenceExpression) exp;
    return null;
  }

  public MemberExpression getCallWithMemberExpression(String literal) {
    Expression exp = callParamMap.get(literal);
    if (exp instanceof MemberExpression) return (MemberExpression) exp;
    return null;
  }

  @Test
  public void testVarNameDeclaredInLoop() {
    DeclaredReferenceExpression asReference = getCallWithReference("func1_first_loop_varName");
    assertNotNull(asReference);
    VariableDeclaration firstLoopLocal =
        Util.getSubnodeOfTypeWithName(forStatements.get(0), VariableDeclaration.class, "varName");
    assertSame(
        asReference.getRefersTo(),
        firstLoopLocal); // Todo refers to the second loop variable, apparently only one is
    // collected and there is no defined scope
  }

  @Test
  public void testVarNameInSecondLoop() {
    DeclaredReferenceExpression asReference = getCallWithReference("func1_second_loop_varName");
    assertNotNull(asReference);
    VariableDeclaration secondLoopLocal =
        Util.getSubnodeOfTypeWithName(forStatements.get(1), VariableDeclaration.class, "varName");
    assertSame(asReference.getRefersTo(), secondLoopLocal);
  }

  @Test
  public void testImplicitThisVarNameAfterLoops() {
    MemberExpression asMemberExpression = getCallWithMemberExpression("func1_imp_this_varName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(), outerImpThis);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        outerVarName); // Todo refers to the second loop local
  }

  @Test
  public void testReferenceToParameter() {
    DeclaredReferenceExpression asReference = getCallWithReference("func2_param_varName");
    assertNotNull(asReference);
    ValueDeclaration param =
        Util.getSubnodeOfTypeWithName(outer_function2, ParamVariableDeclaration.class, "varName");
    assertSame(asReference.getRefersTo(), param);
  }

  @Test
  public void testVarNameInInstanceOfExternalClass() {
    MemberExpression asMemberExpression =
        getCallWithMemberExpression("func3_external_instance_varName");
    assertNotNull(asMemberExpression);
    VariableDeclaration externalClassInstance =
        Util.getSubnodeOfTypeWithName(outer_function3, VariableDeclaration.class, "externalClass");
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(),
        externalClassInstance);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        externVarName); // Todo points to the function parameter with the same name
  }

  @Test
  public void testStaticVarNameInExternalClass() {
    MemberExpression asMemberExpression =
        getCallWithMemberExpression("func3_external_static_staticVarName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(),
        externalClass); // Todo here a Unknown record declaration is added
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        externStaticVarName); // Todo member refers to local variable with the same name of the
    // static field in external
  }

  @Test
  public void testStaticVarnameWithoutPreviousInstance() {
    MemberExpression asMemberExpression =
        getCallWithMemberExpression("func4_external_static_staticVarName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    // Todo Case is a unknown record declaration
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(), externalClass);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        externStaticVarName);
  }

  @Test
  public void testVarNameOverImpThisInnerClass() {
    MemberExpression asMemberExpression =
        getCallWithMemberExpression("func1_inner_imp_this_varName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(), innerImpThis);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(), innerVarName);
  }

  @Test
  public void testVarNameInOuterFromInnerClass() {
    MemberExpression asMemberExpression = getCallWithMemberExpression("func1_outer_this_varName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(), innerImpOuter);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        outerVarName); // Todo Points to varName in inner class instead of outer
  }

  @Test
  public void testStaticOuterFromInner() {
    MemberExpression asMemberExpression =
        getCallWithMemberExpression("func1_outer_static_staticVarName");
    assertNotNull(asMemberExpression);
    assertTrue(asMemberExpression.getBase() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getBase()).getRefersTo(), outerClass);
    assertTrue(asMemberExpression.getMember() instanceof DeclaredReferenceExpression);
    assertSame(
        ((DeclaredReferenceExpression) asMemberExpression.getMember()).getRefersTo(),
        outerStaticVarName); // Todo points to innerStaticVar, this is wrong
  }

  @Test
  public void testParamVarNameInInnerClass() {
    DeclaredReferenceExpression asReference = getCallWithReference("func2_inner_param_varName");
    assertNotNull(asReference);
    assertSame(
        asReference.getRefersTo(),
        Util.getSubnodeOfTypeWithName(inner_function2, ParamVariableDeclaration.class, "varName"));
  }

  @Test
  public void testInnerVarnameOverExplicitThis() {
    MemberExpression asMemberExpression = getCallWithMemberExpression("func2_inner_this_varName");
    assertNotNull(asMemberExpression);
    // Todo memeber currently points to an implicitly created field
    assertSame(asMemberExpression.getMember(), innerVarName);
    assertSame(asMemberExpression.getBase(), innerImpThis);
  }

  @Test
  public void testStaticVarNameAsCoughtExcpetionInInner() {
    DeclaredReferenceExpression asReference =
        getCallWithReference("func3_inner_exception_staticVarName");
    assertNotNull(asReference);
    VariableDeclaration staticVarNameException =
        Util.getSubnodeOfTypeWithName(inner_function3, VariableDeclaration.class, "staticVarName");
    assertSame(asReference.getRefersTo(), staticVarNameException);
  }

  @Test
  public void testVarNameAsCoughtExcpetionInInner() {
    DeclaredReferenceExpression asReference = getCallWithReference("func3_inner_exception_varName");
    assertNotNull(asReference);
    VariableDeclaration varNameExcepetion =
        Util.getSubnodeOfTypeWithName(inner_function3, VariableDeclaration.class, "varName");
    assertSame(asReference.getRefersTo(), varNameExcepetion);
  }
}
