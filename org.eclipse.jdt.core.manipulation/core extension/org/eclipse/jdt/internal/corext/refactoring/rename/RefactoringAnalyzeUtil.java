/*******************************************************************************
 * Copyright (c) 2000, 2019 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Pierre-Yves B. <pyvesdev@gmail.com> - [inline] Allow inlining of local variable initialized to null. - https://bugs.eclipse.org/93850
 *     Microsoft Corporation - copied to jdt.core.manipulation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.text.edits.TextEdit;

import org.eclipse.jface.text.IRegion;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;

import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.SourceRangeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStringStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.util.Messages;

public class RefactoringAnalyzeUtil {

	private RefactoringAnalyzeUtil() {
		//no instances
	}

	public static IRegion[] getNewRanges(TextEdit[] edits, TextChange change){
		IRegion[] result= new IRegion[edits.length];
		for (int i= 0; i < edits.length; i++) {
			result[i]= RefactoringAnalyzeUtil.getNewTextRange(edits[i], change);
		}
		return result;
	}

	public static RefactoringStatus reportProblemNodes(String modifiedWorkingCopySource, SimpleName[] problemNodes) {
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < problemNodes.length; i++) {
			RefactoringStatusContext context= new JavaStringStatusContext(modifiedWorkingCopySource, SourceRangeFactory.create(problemNodes[i]));
			result.addError(Messages.format(RefactoringCoreMessages.RefactoringAnalyzeUtil_name_collision, BasicElementLabels.getJavaElementName(problemNodes[i].getIdentifier())), context);
		}
		return result;
	}

	public static LambdaExpression getLambdaExpression(TextEdit edit, TextChange change, CompilationUnit cuNode) {
		ASTNode decl= RefactoringAnalyzeUtil.findSimpleNameNode(RefactoringAnalyzeUtil.getNewTextRange(edit, change), cuNode);
		return (ASTNodes.getParent(decl, LambdaExpression.class));
	}

	public static MethodDeclaration getMethodDeclaration(TextEdit edit, TextChange change, CompilationUnit cuNode){
		ASTNode decl= RefactoringAnalyzeUtil.findSimpleNameNode(RefactoringAnalyzeUtil.getNewTextRange(edit, change), cuNode);
		return (ASTNodes.getParent(decl, MethodDeclaration.class));
	}

	public static Block getBlock(TextEdit edit, TextChange change, CompilationUnit cuNode){
		ASTNode decl= RefactoringAnalyzeUtil.findSimpleNameNode(RefactoringAnalyzeUtil.getNewTextRange(edit, change), cuNode);
		return (ASTNodes.getParent(decl, Block.class));
	}

	public static IProblem[] getIntroducedCompileProblems(CompilationUnit newCUNode, CompilationUnit oldCuNode) {
		Set<IProblem> subResult= new HashSet<>();
		Set<IProblem> oldProblems= getOldProblems(oldCuNode);
		IProblem[] newProblems= ASTNodes.getProblems(newCUNode, ASTNodes.INCLUDE_ALL_PARENTS, ASTNodes.PROBLEMS);
		for (int i= 0; i < newProblems.length; i++) {
			IProblem correspondingOld= findCorrespondingProblem(oldProblems, newProblems[i]);
			if (correspondingOld == null)
				subResult.add(newProblems[i]);
		}
		return subResult.toArray(new IProblem[subResult.size()]);
	}

	public static RefactoringStatus checkNewSource(CompilationUnitChange change, ICompilationUnit originalCu, CompilationUnit oldCuNode, IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		String newCuSource= change.getPreviewContent(new NullProgressMonitor());
		CompilationUnit newCuNode= new RefactoringASTParser(IASTSharedValues.SHARED_AST_LEVEL).parse(newCuSource, originalCu, true, true, pm);
		IProblem[] newProblems= getIntroducedCompileProblems(newCuNode, oldCuNode);
		for (int i= 0; i < newProblems.length; i++) {
			IProblem problem= newProblems[i];
			if (problem.isError())
				result.addEntry(new RefactoringStatusEntry((problem.isError() ? RefactoringStatus.ERROR : RefactoringStatus.WARNING), problem.getMessage(),
						new JavaStringStatusContext(newCuSource, SourceRangeFactory.create(problem))));
		}
		return result;
	}

	public static IRegion getNewTextRange(TextEdit edit, TextChange change){
		return change.getPreviewEdit(edit).getRegion();
	}

	private static IProblem findCorrespondingProblem(Set<IProblem> oldProblems, IProblem iProblem) {
		for (Iterator<IProblem> iter= oldProblems.iterator(); iter.hasNext();) {
			IProblem oldProblem= iter.next();
			if (isCorresponding(oldProblem, iProblem))
				return oldProblem;
		}
		return null;
	}

	private static boolean isCorresponding(IProblem oldProblem, IProblem iProblem) {
		if (oldProblem.getID() != iProblem.getID())
			return false;
		if (! oldProblem.getMessage().equals(iProblem.getMessage()))
			return false;
		return true;
	}

	private static SimpleName getSimpleName(ASTNode node){
		if (node instanceof SimpleName)
			return (SimpleName)node;
		if (node instanceof VariableDeclaration)
			return ((VariableDeclaration)node).getName();
		return null;
	}

	private static SimpleName findSimpleNameNode(IRegion range, CompilationUnit cuNode) {
		ASTNode node= NodeFinder.perform(cuNode, range.getOffset(), range.getLength());
		return getSimpleName(node);
	}

	private static Set<IProblem> getOldProblems(CompilationUnit oldCuNode) {
		return new HashSet<>(Arrays.asList(ASTNodes.getProblems(oldCuNode, ASTNodes.INCLUDE_ALL_PARENTS, ASTNodes.PROBLEMS)));
	}
}
