package org.eclipse.jdt.ui.tests.refactoring;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.structure.PullUpRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.ui.preferences.JavaPreferencesSettings;

public class PullUpTests extends RefactoringTest {

	private static final Class clazz= PullUpTests.class;
	
	private static final String REFACTORING_PATH= "PullUp/";

	public PullUpTests(String name) {
		super(name);
	}
	
	public static Test suite() {
		return new MySetup(new TestSuite(clazz));
	}

	protected String getRefactoringPath() {
		return REFACTORING_PATH;
	}
	
	//-------------------
	
	private static PullUpRefactoring createRefactoring(IMember[] methods){
		return new PullUpRefactoring(methods, JavaPreferencesSettings.getCodeGenerationSettings());
	}
	
	private void fieldMethodHelper1(String[] fieldNames, String[] methodNames, String[][] signatures, boolean deleteAllInSourceType, boolean deleteAllMatchingMethods) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IField[] fields= TestUtil.getFields(type, fieldNames);
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);

			PullUpRefactoring ref= createRefactoring(TestUtil.merge(methods, fields));
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setSuperclassAsTargetClass(ref);

			if (deleteAllInSourceType)
				ref.setMethodsToDelete(methods);
			if (deleteAllMatchingMethods)
				ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
								
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			String expected= getFileContents(getOutputTestFileName("A"));
			String actual= cu.getSource();
			assertEquals("incorrect modification", expected, actual);
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}

	private IType[] getPossibleTargetClasses(PullUpRefactoring ref) throws JavaModelException{
		return ref.getPossibleTargetClasses(new NullProgressMonitor());
	}
	
	private void setSuperclassAsTargetClass(PullUpRefactoring ref) throws JavaModelException {
		IType[] possibleClasses= getPossibleTargetClasses(ref);
		ref.setTargetClass(possibleClasses[possibleClasses.length - 1]);
	}
	
	private void setTargetClass(PullUpRefactoring ref, int targetClassIndex) throws JavaModelException{
		IType[] possibleClasses= getPossibleTargetClasses(ref);
		ref.setTargetClass(getPossibleTargetClasses(ref)[possibleClasses.length - 1 - targetClassIndex]);
	}
	
	private void addRequiredMembersHelper(String[] fieldNames, String[] methodNames, String[][] methodSignatures, String[] expectedFieldNames, String[] expectedMethodNames, String[][] expectedMethodSignatures) throws Exception {
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IField[] fields= TestUtil.getFields(type, fieldNames);
			IMethod[] methods= TestUtil.getMethods(type, methodNames, methodSignatures);

			PullUpRefactoring ref= createRefactoring(TestUtil.merge(methods, fields));
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setSuperclassAsTargetClass(ref);

			List  required= Arrays.asList(ref.getRequiredPullableMembers(new NullProgressMonitor()));
			IField[] expectedFields= TestUtil.getFields(type, expectedFieldNames);
			IMethod[] expectedMethods= TestUtil.getMethods(type, expectedMethodNames, expectedMethodSignatures);
			List expected= Arrays.asList(TestUtil.merge(expectedFields, expectedMethods));
			assertEquals("incorrect size", expected.size(), required.size());
			for (Iterator iter= expected.iterator(); iter.hasNext();) {
				Object each= (Object) iter.next();
				assertTrue ("required does not contain " + each, required.contains(each));
			}
			for (Iterator iter= required.iterator(); iter.hasNext();) {
				Object each= (Object) iter.next();
				assertTrue ("expected does not contain " + each, expected.contains(each));
			}
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}

	private void fieldHelper1(String[] fieldNames, boolean deleteAllInSourceType, boolean deleteAllMatchingFields, int targetClassIndex) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IField[] fields= TestUtil.getFields(type, fieldNames);
			
			PullUpRefactoring ref= createRefactoring(fields);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setTargetClass(ref, targetClassIndex);
		
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			String expected= getFileContents(getOutputTestFileName("A"));
			String actual= cu.getSource();
			assertEquals("incorrect modification", expected, actual);
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}
	
	private void fieldHelper2(String[] fieldNames, int targetClassIndex) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IField[] fields= TestUtil.getFields(type, fieldNames);
			PullUpRefactoring ref= createRefactoring(fields);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setTargetClass(ref, targetClassIndex);

			RefactoringStatus result= performRefactoring(ref);
			assertTrue("precondition was supposed to fail", result != null && ! result.isOK());
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}		
	}
	
	private static IMethod[] getMethods(IMember[] members){
		List l= Arrays.asList(JavaElementUtil.getElementsOfType(members, IJavaElement.METHOD));
		return (IMethod[]) l.toArray(new IMethod[l.size()]);
	}
	
	private void helper1(String[] methodNames, String[][] signatures, boolean deleteAllInSourceType, boolean deleteAllMatchingMethods, int targetClassIndex) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());

			setTargetClass(ref, targetClassIndex);
			if (deleteAllInSourceType)
				ref.setMethodsToDelete(methods);
			if (deleteAllMatchingMethods)
				ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			String expected= getFileContents(getOutputTestFileName("A"));
			String actual= cu.getSource();
			//assertEquals("incorrect lengths", expected.length(), actual.length());
			assertEquals("incorrect modification", expected, actual);
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}	
	}
	
	private void helper2(String[] methodNames, String[][] signatures, boolean deleteAllInSourceType, boolean deleteAllMatchingMethods, int targetClassIndex) throws Exception{
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setTargetClass(ref, targetClassIndex);
			if (deleteAllInSourceType)
				ref.setMethodsToDelete(methods);
			if (deleteAllMatchingMethods)
				ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertTrue("precondition was supposed to fail", result != null && ! result.isOK());
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}		
	}
	
	private void helper3(String[] methodNames, String[][] signatures, boolean deleteAllInSourceType, boolean deleteAllMatchingMethods, int targetClassIndex, boolean shouldActivationCheckPass) throws Exception {
		ICompilationUnit cuA= createCUfromTestFile(getPackageP(), "A");
		ICompilationUnit cuB= createCUfromTestFile(getPackageP(), "B");
		try{
			IType type= getType(cuB, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertEquals("activation", shouldActivationCheckPass, ref.checkActivation(new NullProgressMonitor()).isOK());
			if (! shouldActivationCheckPass)
				return;
			setTargetClass(ref, targetClassIndex);			
			if (deleteAllInSourceType)
				ref.setMethodsToDelete(methods);
			if (deleteAllMatchingMethods)
				ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
			
			RefactoringStatus result= performRefactoring(ref);
			assertTrue("precondition was supposed to fail", result != null && ! result.isOK());
		} finally{
			performDummySearch();
			cuA.delete(false, null);
			cuB.delete(false, null);
		}				
	}
	
	//------------------ tests -------------
	
	public void test0() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test1() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test2() throws Exception{
		helper1(new String[]{"mmm", "n"}, new String[][]{new String[0], new String[0]}, true, false, 0);
	}

	public void test3() throws Exception{
		helper1(new String[]{"mmm", "n"}, new String[][]{new String[0], new String[0]}, true, true, 0);
	}

	public void test4() throws Exception{
		ICompilationUnit cuA= createCUfromTestFile(getPackageP(), "A");
		ICompilationUnit cuB= createCUfromTestFile(getPackageP(), "B");
		
		try{
			String[] methodNames= new String[]{"m"};
			String[][] signatures= new String[][]{new String[]{"QList;"}};
			
			IType type= getType(cuB, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setSuperclassAsTargetClass(ref);

			ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			assertEquals("incorrect modification in A", getFileContents(getOutputTestFileName("A")), cuA.getSource());		
			assertEquals("incorrect modification in B", getFileContents(getOutputTestFileName("B")), cuB.getSource());		
		} finally{
			performDummySearch();
			cuA.delete(false, null);
			cuB.delete(false, null);
		}					
	}

	public void test5() throws Exception{
		ICompilationUnit cuA= createCUfromTestFile(getPackageP(), "A");
		ICompilationUnit cuB= createCUfromTestFile(getPackageP(), "B");
		
		try{
			String[] methodNames= new String[]{"m"};
			String[][] signatures= new String[][]{new String[0]};
			
			IType type= getType(cuB, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setSuperclassAsTargetClass(ref);

			ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			assertEquals("incorrect modification in A", getFileContents(getOutputTestFileName("A")), cuA.getSource());
			assertEquals("incorrect modification in B", getFileContents(getOutputTestFileName("B")), cuB.getSource());		
		} finally{
			performDummySearch();
			cuA.delete(false, null);
			cuB.delete(false, null);
		}		
	}

	public void test6() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test7() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test8() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test9() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test10() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test11() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test12() throws Exception{
		//printTestDisabledMessage("bug#6779 searchDeclarationsOfReferencedTyped - missing exception  types");
		ICompilationUnit cuA= createCUfromTestFile(getPackageP(), "A");
		ICompilationUnit cuB= createCUfromTestFile(getPackageP(), "B");

		try{
			String[] methodNames= new String[]{"m"};
			String[][] signatures= new String[][]{new String[0]};
			
			IType type= getType(cuB, "B");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			PullUpRefactoring ref= createRefactoring(methods);
			assertTrue("preactivation", ref.checkPreactivation().isOK());
			assertTrue("activation", ref.checkActivation(new NullProgressMonitor()).isOK());
			setSuperclassAsTargetClass(ref);
			ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertEquals("precondition was supposed to pass", null, result);
			
			assertEquals("incorrect modification in A", getFileContents(getOutputTestFileName("A")), cuA.getSource());		
			assertEquals("incorrect modification in B", getFileContents(getOutputTestFileName("B")), cuB.getSource());		
		} finally{
			performDummySearch();
			cuA.delete(false, null);
			cuB.delete(false, null);
		}							
	}
	
	public void test13() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test14() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test15() throws Exception{
		printTestDisabledMessage("must fix - incorrect error");
//		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}
	
	public void test16() throws Exception{
		printTestDisabledMessage("must fix - incorrect error");
//		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}
	
	public void test17() throws Exception{
		printTestDisabledMessage("must fix - incorrect error with static method access");
//		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}

	public void test18() throws Exception{
		printTestDisabledMessage("must fix - incorrect error with static field access");
//		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}
	
	public void test19() throws Exception{
//		printTestDisabledMessage("bug 18438");
//		printTestDisabledMessage("bug 23324 ");
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void test20() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 1);
	}

	public void test21() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 1);
	}

	public void test22() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test23() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test24() throws Exception{
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void test25() throws Exception{
//		printTestDisabledMessage("bug in ASTRewrite - extra dimentions 29553");
		helper1(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void testFail0() throws Exception{
//		printTestDisabledMessage("6538: searchDeclarationsOf* incorrect");
		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void testFail1() throws Exception{
		printTestDisabledMessage("overloading - current limitation");
//		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}
	
	public void testFail2() throws Exception{
//		printTestDisabledMessage("6538: searchDeclarationsOf* incorrect");
		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void testFail3() throws Exception{
//		printTestDisabledMessage("6538: searchDeclarationsOf* incorrect");
		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void testFail4() throws Exception{
//		printTestDisabledMessage("6538: searchDeclarationsOf* incorrect");
		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}

	public void testFail5() throws Exception{
		helper2(new String[]{"m"}, new String[][]{new String[0]}, true, false, 0);
	}
	
	public void testFail6() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, true);
	}
	
	public void testFail7() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, false);
	}
	
	public void testFail8() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, true);
	}
	
	public void testFail9() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, true);
	}
	
	public void testFail10() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, false);
	}

	public void testFail11() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0, true);
	}

	public void testFail12() throws Exception{
		printTestDisabledMessage("overloading - current limitation");
//		String[] methodNames= new String[]{"m"};
//		String[][] signatures= new String[][]{new String[0]};
//		boolean deleteAllInSourceType= true;
//		boolean deleteAllMatchingMethods= false;
//		helper3(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods);
	}

	public void testFail13() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0);
	}
	
	public void testFail14() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		ICompilationUnit cu= createCUfromTestFile(getPackageP(), "A");
		try{
			IType type= getType(cu, "A");
			IMethod[] methods= TestUtil.getMethods(type, methodNames, signatures);
			IMember[] members= TestUtil.merge(methods, new IMember[]{type.getType("Quux")});
			PullUpRefactoring ref= createRefactoring(members);
			if (deleteAllInSourceType)
				ref.setMethodsToDelete(methods);
			if (deleteAllMatchingMethods)
				ref.setMethodsToDelete(getMethods(ref.getMatchingElements(new NullProgressMonitor())));
		
			RefactoringStatus result= performRefactoring(ref);
			assertTrue("precondition was supposed to fail", result != null && ! result.isOK());
		} finally{
			performDummySearch();
			cu.delete(false, null);
		}		
	}
	
	public void testFail15() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 1);
	}

	public void testFail16() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 1);
	}

	public void testFail17() throws Exception{
		printTestDisabledMessage("unimplemented test - see bug 29522");
//		String[] methodNames= new String[]{"m"};
//		String[][] signatures= new String[][]{new String[0]};
//		boolean deleteAllInSourceType= true;
//		boolean deleteAllMatchingMethods= false;
//		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 1);
	}

	public void testFail18() throws Exception{
		printTestDisabledMessage("unimplemented test - see bug 29522");
//		String[] methodNames= new String[]{"m"};
//		String[][] signatures= new String[][]{new String[0]};
//		boolean deleteAllInSourceType= true;
//		boolean deleteAllMatchingMethods= false;
//		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 0);
	}

	public void testFail19() throws Exception{
		String[] methodNames= new String[]{"m"};
		String[][] signatures= new String[][]{new String[0]};
		boolean deleteAllInSourceType= true;
		boolean deleteAllMatchingMethods= false;
		helper2(methodNames, signatures, deleteAllInSourceType, deleteAllMatchingMethods, 1);
	}

	//----------------------------------------------------------
	public void testField0() throws Exception{
		fieldHelper1(new String[]{"i"}, true, false, 0);
	}
	
	public void testFieldFail0() throws Exception{
		fieldHelper2(new String[]{"x"}, 0);
	}
	
	public void testFieldFail1() throws Exception{
		fieldHelper2(new String[]{"x"}, 0);
	}

	public void testFieldFail2() throws Exception{
		fieldHelper2(new String[]{"f"}, 1);
	}

	//---------------------------------------------------------
	public void testFieldMethod0() throws Exception{
//		printTestDisabledMessage("bug 23324 ");
		fieldMethodHelper1(new String[]{"f"}, new String[]{"m"}, new String[][]{new String[0]}, true, false);
	}
	
	//----
	public void testAddingRequiredMembers0() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= methodNames;
		String[][] expectedMethodSignatures= methodSignatures;
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers1() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= methodNames;
		String[][] expectedMethodSignatures= methodSignatures;
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers2() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= methodNames;
		String[][] expectedMethodSignatures= methodSignatures;
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers3() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= {"m", "y"};
		String[][] expectedMethodSignatures= {new String[0], new String[0]};
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers4() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= {"m", "y"};
		String[][] expectedMethodSignatures= {new String[0], new String[0]};
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers5() throws Exception{
		String[] fieldNames= {"y"};
		String[] methodNames= {};
		String[][] methodSignatures= {};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= {"m"};
		String[][] expectedMethodSignatures= {new String[0]};
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}

	public void testAddingRequiredMembers6() throws Exception{
		String[] fieldNames= {};
		String[] methodNames= {"m"};
		String[][] methodSignatures= {new String[0]};
		
		String[] expectedFieldNames= fieldNames;
		String[] expectedMethodNames= methodNames;
		String[][] expectedMethodSignatures= methodSignatures;
		addRequiredMembersHelper(fieldNames, methodNames, methodSignatures, expectedFieldNames, expectedMethodNames, expectedMethodSignatures);
	}
	
}

