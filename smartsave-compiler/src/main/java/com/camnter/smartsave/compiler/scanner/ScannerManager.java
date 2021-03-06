package com.camnter.smartsave.compiler.scanner;

import com.squareup.javapoet.ClassName;
import com.sun.source.util.Trees;
import com.sun.tools.javac.tree.JCTree;
import java.lang.annotation.Annotation;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 编译阶段扫描指定注解所在 packageName 所属的的 资源 id（ R class 内的 ）
 *
 * @author CaMnter
 */

public final class ScannerManager {

    static final List<String> SUPPORTED_TYPES = Arrays.asList(
        "array", "attr", "bool", "color", "dimen", "drawable", "id", "integer", "string"
    );

    private final ProcessingEnvironment processingEnvironment;
    private final Elements elementUtils;
    private final Types typeUtils;

    private final Map<QualifiedId, Id> symbols = new LinkedHashMap<>();
    private final Messager messager;
    private Trees trees;


    private ScannerManager(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        this.elementUtils = this.processingEnvironment.getElementUtils();
        this.typeUtils = this.processingEnvironment.getTypeUtils();
        this.messager = processingEnvironment.getMessager();
        try {
            this.trees = Trees.instance(this.processingEnvironment);
        } catch (IllegalArgumentException ignored) {
        }
    }


    public static ScannerManager get(ProcessingEnvironment processingEnvironment) {
        return new ScannerManager(processingEnvironment);
    }


    /**
     * 获取 注解 在元素上对应的 AnnotationMirror
     * 目前仅为了生成 JCTree
     *
     * @param element 注解元素
     * @param annotation 注解 class 类型
     * @return AnnotationMirror
     */
    private AnnotationMirror getMirror(Element element,
                                       Class<? extends Annotation> annotation) {
        for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
            if (annotationMirror.getAnnotationType()
                .toString()
                .equals(annotation.getCanonicalName())) {
                return annotationMirror;
            }
        }
        return null;
    }


    /**
     * 扫描 R class
     * 解析 R class
     *
     * @param env RoundEnvironment
     * @param supportedAnnotations 注解集合
     */
    public void scanForRClasses(RoundEnvironment env,
                                Set<Class<? extends Annotation>> supportedAnnotations) {
        if (trees == null) return;

        RClassScanner scanner = new RClassScanner();

        /*
         * 每个注解类型 与 被注解的元素，生成一棵树
         * 然后设置 R class 扫描这个元素的 package name
         * 最后让这棵树 会自动调用扫描类中的方法去，扫描 R class
         */
        for (Class<? extends Annotation> annotation : supportedAnnotations) {
            for (Element element : env.getElementsAnnotatedWith(annotation)) {
                JCTree tree = (JCTree) this.trees.getTree(element, getMirror(element, annotation));
                // tree can be null if the references are compiled types and not source
                if (tree != null) {
                    String respectivePackageName =
                        this.elementUtils.getPackageOf(element).getQualifiedName().toString();
                    scanner.setCurrentPackageName(respectivePackageName);
                    tree.accept(scanner);
                }
            }
        }

        /*
         * 拿到全部 R classes
         * 然后 parseRClass(...) 解析 R class
         */
        for (Map.Entry<String, Set<String>> packageNameToRClassSet : scanner.getRClasses()
            .entrySet()) {
            String respectivePackageName = packageNameToRClassSet.getKey();
            for (String rClass : packageNameToRClassSet.getValue()) {
                parseRClass(respectivePackageName, rClass);
            }
        }

        // print id
        Deque<Map.Entry<QualifiedId, Id>> entries = new ArrayDeque<>(this.symbols.entrySet());
        while (!entries.isEmpty()) {
            Map.Entry<QualifiedId, Id> entry = entries.removeFirst();
            QualifiedId qualifiedId = entry.getKey();
            // this.messager.printMessage(
            //     Diagnostic.Kind.NOTE,
            //     "[packageName] = " + qualifiedId.packageName +
            //         "   [fullName] = " + qualifiedId.enclosingElementName + "." +
            //         qualifiedId.elementSimpleName +
            //         "   [simpleName] = " + qualifiedId.elementSimpleName +
            //         "\n  [id] = " + qualifiedId.id
            // );
        }

    }


    /**
     * 1.获取 该 R class 在 scanForRClasses(...) 时，生辰生成的那棵树
     * - 如果存在树，就解析编译好的 R class
     * - 不存在的话，创建一个 Id 扫描类，扫描 R class 内的所有 Id
     * 2.Id 扫描类，内还会让树调用 Var 扫描类，扫描全部 int 变量
     *
     * @param respectivePackageName R class package name
     * @param rClass R class
     */
    private void parseRClass(String respectivePackageName, String rClass) {
        Element element;

        try {
            element = elementUtils.getTypeElement(rClass);
        } catch (MirroredTypeException mte) {
            element = typeUtils.asElement(mte.getTypeMirror());
        }

        JCTree tree = (JCTree) trees.getTree(element);
        if (tree != null) { // tree can be null if the references are compiled types and not source
            IdScanner idScanner = new IdScanner(this.symbols,
                this.elementUtils.getPackageOf(element)
                    .getQualifiedName().toString(), respectivePackageName);
            tree.accept(idScanner);
        } else {
            parseCompiledR(respectivePackageName, (TypeElement) element);
        }
    }


    /**
     * 解析编译过的 R class
     *
     * @param respectivePackageName package name
     * @param rClass R class
     */
    private void parseCompiledR(String respectivePackageName, TypeElement rClass) {
        for (Element element : rClass.getEnclosedElements()) {
            String innerClassName = element.getSimpleName().toString();
            if (SUPPORTED_TYPES.contains(innerClassName)) {
                for (Element enclosedElement : element.getEnclosedElements()) {
                    if (enclosedElement instanceof VariableElement) {
                        VariableElement variableElement = (VariableElement) enclosedElement;
                        Object value = variableElement.getConstantValue();

                        if (value instanceof Integer) {
                            int id = (Integer) value;
                            ClassName rClassName =
                                ClassName.get(
                                    this.elementUtils.getPackageOf(variableElement).toString(),
                                    "R",
                                    innerClassName);
                            String resourceName = variableElement.getSimpleName().toString();
                            QualifiedId qualifiedId = new QualifiedId(
                                respectivePackageName,
                                element.getSimpleName().toString(),
                                enclosedElement.getSimpleName().toString(),
                                id
                            );
                            this.symbols.put(qualifiedId, new Id(id, rClassName, resourceName));
                        }
                    }
                }
            }
        }
    }

}
