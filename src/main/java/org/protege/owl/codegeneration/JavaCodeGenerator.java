package org.protege.owl.codegeneration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataRange;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyFormat;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

/**
 * A class that can create Java interfaces in the Protege-OWL format
 * 
 * @author z.khan
 * 
 */
public class JavaCodeGenerator {
	public static final Logger LOGGER = Logger.getLogger(JavaCodeGenerator.class);

    private CodeGenerationOptions options;

    List<Node<OWLClass>> classesNodeList;
    private OWLReasoner reasoner;
    private OWLOntology owlOntology;
    private Set<OWLObjectProperty> objectProperties = new HashSet<OWLObjectProperty>();;
    private Set<OWLDataProperty> dataProperties = new HashSet<OWLDataProperty>();;
    private DefaultPrefixManager prefixManager;
    private PrintWriter vocabularyPrintWriter;
    private FileWriter vocabularyfileWriter;
    private Set<OWLOntology> importedOntologies;
    private Map<String, String> prefixMap = new HashMap<String, String>();
    private Set<OWLOntology> allOwlOntologies = new HashSet<OWLOntology>();

    /**Constructor
     * @param owlOntology
     * @param options
     */
    public JavaCodeGenerator(OWLOntology owlOntology, CodeGenerationOptions options) {

        this.owlOntology = owlOntology;
        this.options = options;
        File folder = options.getOutputFolder();
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        String pack = options.getPackage();
        if (pack != null) {
            pack = pack.replace('.', '/');
            File file = folder == null ? new File(pack) : new File(folder, pack);
            file.mkdirs();
            File f = new File(file, "impl");
            f.mkdirs();
        } else {
            File file = folder == null ? new File("impl") : new File(folder, "impl");
            file.mkdirs();
        }
    }

    /**Initiates the code generation
     * @param reasoner
     * @throws IOException
     */
    public void createAll(OWLReasoner reasoner) throws IOException {

        this.reasoner = reasoner;
        setAllOntologies();
        getOntologyObjectProperties();
        getOntologyDataProperties();
        setOntologyPrefixMap();
        Node<OWLClass> topNode = this.reasoner.getTopClassNode();
        List<OWLClass> owlClassList = getClassesList(topNode);
        printVocabularyCode(owlClassList);
        printFactoryClassCode(owlClassList);
        for (Iterator iterator = owlClassList.iterator(); iterator.hasNext();) {
            OWLClass owlClass = (OWLClass) iterator.next();
            createInterface(owlClass);
            createImplementation(owlClass);
        }
    }

    /**
     * Retrieves the onlology uri to prefix mapping and adds the pair in Map 
     */
    protected void setOntologyPrefixMap() {
        for (OWLOntology importedOntology : importedOntologies) {
            OWLOntologyFormat format = importedOntology.getOWLOntologyManager().getOntologyFormat(importedOntology);
            if (format.isPrefixOWLOntologyFormat()) {
                Map<String, String> map = format.asPrefixOWLOntologyFormat().getPrefixName2PrefixMap();
                for (Map.Entry<String, String> m : map.entrySet()) {
                    if (m.getValue().toString().equals(
                            importedOntology.getOntologyID().getOntologyIRI().toString() + "#")) {
                        String prefixFormatted = m.getKey().toString().replace(":", "").trim();
                        if (prefixFormatted.length() > 0) {
                            prefixMap
                                    .put(importedOntology.getOntologyID().getOntologyIRI().toString(), prefixFormatted);
                        }
                    }

                }
            }
        }
    }

    /**
     * Adds the ontology and all its imported ontology in a set.
     */
    private void setAllOntologies() {

        importedOntologies = owlOntology.getImports();
        allOwlOntologies.add(owlOntology);
        allOwlOntologies.addAll(importedOntologies);
    }

    /** Generates and returns the interface name of the provided owlClass
     * @param owlClass The class whose interface name is to be returned
     * @return The interface name.
     */
    protected String getInterfaceName(OWLClass owlClass) {
        OWLOntology classOntology = getParentOntology(owlClass.getIRI());
        String classOntologyIRI;
        if (classOntology != null) {
            classOntologyIRI = classOntology.getOntologyID().getOntologyIRI().toString();
        } else {
            classOntologyIRI = owlOntology.getOntologyID().getOntologyIRI().toString();
        }
        String namePrefix = prefixMap.get(classOntologyIRI);
        if (prefixManager == null || !prefixManager.getDefaultPrefix().startsWith(classOntologyIRI)) {
            prefixManager = new DefaultPrefixManager(classOntologyIRI + "#");
        }

        String interfaceName = prefixManager.getShortForm(owlClass);
        interfaceName = interfaceName.replace(":", "");

        if (options.getPrefixMode() && namePrefix != null) {
            namePrefix = getInitialLetterAsUpperCase(namePrefix);
            interfaceName = getInitialLetterAsUpperCase(interfaceName);
            interfaceName = namePrefix + "_" + interfaceName;
        }
        return interfaceName;
    }

    /**
     * Retrives the OWLOntology to which the owlClass belongs to.
     * 
     * @param owlClass
     * @return The iri of the ontology to which the OWLClass belongs
     */
    private OWLOntology getParentOntology(IRI iri) {

        for (OWLOntology ontology : allOwlOntologies) {
            if (iri.toString().startsWith(ontology.getOntologyID().getOntologyIRI().toString())) {
                return ontology;
            }
        }
        return null;
    }

    /** Generates and returns the interface name of provided owlClass. 
     *  The function appends a prefix to the interface name if abstract 
     *  mode is set to true.
     * @param owlClass
     * @return
     */
    protected String getInterfaceNamePossiblyAbstract(OWLClass owlClass) {
        String interfaceName = getInterfaceName(owlClass);
        if (options.getAbstractMode()) {
            interfaceName += "_";
        }
        return interfaceName;
    }

    /** Generates and returns the name of the object property
     * @param owlObjectProperty The property whose name is to be returned.
     * @return The property name.
     */
    protected String getObjectPropertyName(OWLObjectProperty owlObjectProperty) {

        String propertyName = owlObjectProperty.getIRI().getFragment();
        if (options.getPrefixMode() && propertyName != null) {
            propertyName = getInitialLetterAsUpperCase(propertyName);

            OWLOntology classOntology = getParentOntology(owlObjectProperty.getIRI());
            String classOntologyIRI;
            if (classOntology != null) {
                classOntologyIRI = classOntology.getOntologyID().getOntologyIRI().toString();
            } else {
                classOntologyIRI = owlOntology.getOntologyID().getOntologyIRI().toString();
            }
            String namePrefix = prefixMap.get(classOntologyIRI);
            if (namePrefix != null) {
                namePrefix = getInitialLetterAsUpperCase(namePrefix);
                propertyName = getInitialLetterAsUpperCase(propertyName);
                propertyName = namePrefix + "_" + propertyName;
            }
        }
        return propertyName;
    }

    /**
     * Gets all the Object Properties of the Ontology
     */
    private void getOntologyObjectProperties() {
        for (OWLOntology ontology : allOwlOntologies) {
            objectProperties.addAll(ontology.getObjectPropertiesInSignature());
        }
    }

    /**
     * Gets all the Data Properties of the Ontology
     */
    private void getOntologyDataProperties() {
        for (OWLOntology ontology : allOwlOntologies) {
            dataProperties.addAll(ontology.getDataPropertiesInSignature());
        }
    }

    /**
     * Generates interface code for the provided OWlClass
     * 
     * @param owlClass The class whose interface code is to generated
     * @throws IOException
     */
    private void createInterface(OWLClass owlClass) throws IOException {

        String interfaceName = getInterfaceNamePossiblyAbstract(owlClass);
        File baseFile = getInterfaceFile(interfaceName);
        FileWriter fileWriter = new FileWriter(baseFile);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printInterfaceCode(interfaceName, owlClass, printWriter);
        fileWriter.close();

        if (options.getAbstractMode()) {
            createUserInterface(owlClass);
        }
    }

    /**
     * Writes the interface code for the provided OWlClass to the PrintStream
     * 
     * @param interfaceName 
     * @param owlClass
     * @param printWriter
     */
    private void printInterfaceCode(String interfaceName, OWLClass owlClass, PrintWriter printWriter) {
        printInterfacePackageStatement(printWriter);

        List<OWLObjectProperty> owlObjectProperties = getClassObjectProperties(owlClass);
        List<OWLDataProperty> owlDataProperties = getClassDataProperties(owlClass);

        printWriter.println("import org.semanticweb.owlapi.model.*;");
        printWriter.println();

        addImportJavaUtilCode(printWriter, owlDataProperties, owlObjectProperties);

        printWriter.println("/**");
        printWriter.println(" * Generated by Protege (http://protege.stanford.edu).");
        printWriter.println(" * Source Class: " + interfaceName);
        printWriter.println(" *");
        printWriter.println(" * @version generated on " + new Date());
        printWriter.println(" */");
        printWriter.println("public interface " + interfaceName + getInterfaceExtendsCode(owlClass) + " {");

        for (Iterator<OWLObjectProperty> iterator = owlObjectProperties.iterator(); iterator.hasNext();) {
            OWLObjectProperty owlObjectProperty = iterator.next();
            printInterfaceObjectPropertyCode(owlObjectProperty, printWriter);

            printWriter.println();

        }
        for (Iterator<OWLDataProperty> iterator = owlDataProperties.iterator(); iterator.hasNext();) {
            OWLDataProperty owlDataProperty = iterator.next();
            printInterfaceDataPropertyCode(owlDataProperty, printWriter);
        }
        printWriter.println();
        printWriter.println("    void delete();");
        printWriter.println("}");

    }

    /**
     * Writes the interface object code for the provided OWLObjectProperty to the PrintStream
     * @param owlObjectProperty
     * @param printWriter
     */
    private void printInterfaceObjectPropertyCode(OWLObjectProperty owlObjectProperty, PrintWriter printWriter) {
        String propertyName = getObjectPropertyName(owlObjectProperty);
        String propertyNameUpperCase = getInitialLetterAsUpperCase(propertyName);
        printWriter.println();
        printWriter.println("    // Property " + owlObjectProperty.getIRI());
        printWriter.println();
        printWriter.println("    " + getObjectPropertyRange(owlObjectProperty, false) + " get" + propertyNameUpperCase
                + "();");
        printWriter.println();
        printWriter.println("    " + "OWLObjectProperty get" + propertyNameUpperCase + "Property();");
        printWriter.println();
        printWriter.println("    boolean has" + propertyNameUpperCase + "();");

        if (!owlObjectProperty.isFunctional(allOwlOntologies)) {
            Set<OWLClassExpression> oClassExpressions = owlObjectProperty.getRanges(allOwlOntologies);
            String objPropertyJavaName = getObjectPropertyJavaName(oClassExpressions);
            printWriter.println();
            printWriter
                    .println("    " + PropertyConstants.JAVA_UTIL_ITERATOR + " list" + propertyNameUpperCase + "();");
            printWriter.println();
            printWriter.println("    void add" + propertyNameUpperCase + "(" + objPropertyJavaName + " new"
                    + propertyNameUpperCase + ");");
            printWriter.println();
            printWriter.println("    void remove" + propertyNameUpperCase + "(" + objPropertyJavaName + " old"
                    + propertyNameUpperCase + ");");
        }

        printWriter.println();
        printWriter.println("    void set" + propertyNameUpperCase + "("
                + getObjectPropertyRange(owlObjectProperty, false) + " new" + propertyNameUpperCase + ");");
    }

    /**
     * Retrieves the range for the provided OWLObjectProperty
     * @param owlObjectProperty The property whose range is to be returned
     * @param useExtends
     * @return
     */
    private String getObjectPropertyRange(OWLObjectProperty owlObjectProperty, boolean useExtends) {
        Set<OWLClassExpression> oClassExpressions = owlObjectProperty.getRanges(allOwlOntologies);
        String objPropertyRange = getObjectPropertyJavaName(oClassExpressions);
        if (owlObjectProperty.isFunctional(allOwlOntologies)) {// property can
            // contain only
            // single value
            return objPropertyRange;
        } else {// Property contains multiple values

            if (oClassExpressions.size() > 1) { // Contains More than 1 range
                // hence disable use of extends
                useExtends = false;
            }
            String genericsString = objPropertyRange.equals(PropertyConstants.JAVA_LANG_OBJECT) ? ""
                    : useExtends ? "<? extends " + objPropertyRange + ">" : "<" + objPropertyRange + ">";
            objPropertyRange = options.getSetMode() ? "Set" + genericsString : "Collection" + genericsString;
            return objPropertyRange;
        }
    }

    /** Returns a Java name for the object property
     * @param objPropertyRange
     * @param oClassExpressions
     * @return
     */
    private String getObjectPropertyJavaName(Set<OWLClassExpression> oClassExpressions) {
        String objPropertyRange = null;
        if (oClassExpressions == null || oClassExpressions.isEmpty() || oClassExpressions.size() > 1) {// If count of range is other
            // then One (zero of more
            // than 1 ) then return
            // range as java.lang.Object
            objPropertyRange = PropertyConstants.JAVA_LANG_OBJECT;
        } else {
            for (OWLClassExpression owlClassExpression : oClassExpressions) {
                try {
                    OWLClass owlClass = owlClassExpression.asOWLClass();
                    objPropertyRange = getInterfaceName(owlClass);
                    break;
                } catch (Exception e) {
                    continue;
                }
            }
        }
        return objPropertyRange;
    }

    /**
     * @param owlDataProperty
     * @param printWriter
     */
    private void printInterfaceDataPropertyCode(OWLDataProperty owlDataProperty, PrintWriter printWriter) {
        String propertyName = getDataPropertyName(owlDataProperty);
        String propertyNameUpperCase = getInitialLetterAsUpperCase(propertyName);
        printWriter.println();
        printWriter.println();
        printWriter.println("    // Property " + owlDataProperty.getIRI());
        printWriter.println();
        printWriter.println("    " + getDataPropertyRange(owlDataProperty) + " get" + propertyNameUpperCase + "();");
        printWriter.println();
        printWriter.println("    " + "OWLDataProperty get" + propertyNameUpperCase + "Property();");
        printWriter.println();
        printWriter.println("    boolean has" + propertyNameUpperCase + "();");
        if (!owlDataProperty.isFunctional(allOwlOntologies)) {
            Set<OWLDataRange> owlDataRanges = owlDataProperty.getRanges(allOwlOntologies);
            printWriter.println();
            printWriter
                    .println("    " + PropertyConstants.JAVA_UTIL_ITERATOR + " list" + propertyNameUpperCase + "();");
            printWriter.println();
            printWriter.println("    void add" + propertyNameUpperCase + "(" + getDataPropertyJavaName(owlDataRanges)
                    + " new" + propertyNameUpperCase + ");");
            printWriter.println();
            printWriter.println("    void remove" + propertyNameUpperCase + "("
                    + getDataPropertyJavaName(owlDataRanges) + " old" + propertyNameUpperCase + ");");

        }
        printWriter.println();
        printWriter.println("    void set" + propertyNameUpperCase + "(" + getDataPropertyRange(owlDataProperty)
                + " new" + propertyNameUpperCase + ");");

    }

    private String getDataPropertyRange(OWLDataProperty owlDataProperty) {
        Set<OWLDataRange> owlDataRanges = owlDataProperty.getRanges(allOwlOntologies);
        String dataPropertyRange = getDataPropertyJavaName(owlDataRanges);

        if (owlDataProperty.isFunctional(allOwlOntologies)) {// property can
            // contain only
            // single value
            return dataPropertyRange;
        } else {
            String genericsString = dataPropertyRange.equals(PropertyConstants.JAVA_LANG_OBJECT) ? "" : "<"
                    + dataPropertyRange + ">";
            dataPropertyRange = options.getSetMode() ? "Set" + genericsString : "Collection" + genericsString;
            return dataPropertyRange;
        }

    }

    /**
     * @param dataPropertyRange
     * @param owlDataRanges
     * @return
     */
    private String getDataPropertyJavaName(Set<OWLDataRange> owlDataRanges) {
        String dataPropertyRange = null;
        if (owlDataRanges == null || owlDataRanges.isEmpty() || owlDataRanges.size() > 1) {
            dataPropertyRange = PropertyConstants.JAVA_LANG_OBJECT;
        } else {
            dataPropertyRange = getOwlDataTypeAsString(owlDataRanges, dataPropertyRange);
        }
        return dataPropertyRange;
    }

    /**
     * @param owlDataRanges
     * @param dataPropertyRange
     * @return
     */
    private String getOwlDataTypeAsString(Set<OWLDataRange> owlDataRanges, String dataPropertyRange) {
        for (OWLDataRange owlDataRange : owlDataRanges) {
            OWLDatatype owlDatatype = owlDataRange.asOWLDatatype();
            IRI dataTypeIRI = owlDatatype.getIRI();
            String dataTypeFragment = dataTypeIRI.getFragment();

            if (owlDatatype.isBoolean()) {
                dataPropertyRange = PropertyConstants.BOOLEAN;
            } else if (owlDatatype.isDouble()) {
                dataPropertyRange = PropertyConstants.DOUBLE;
            } else if (owlDatatype.isFloat()) {
                dataPropertyRange = PropertyConstants.FLOAT;
            } else if (owlDatatype.isInteger() || dataTypeFragment.trim().equals(PropertyConstants.INT)) {
                dataPropertyRange = PropertyConstants.INTEGER;
            } else if (owlDatatype.isString()) {
                dataPropertyRange = PropertyConstants.STRING;
            } else {
                dataPropertyRange = PropertyConstants.JAVA_LANG_OBJECT;
            }
            break;
        }
        return dataPropertyRange;
    }

    protected String getDataPropertyName(OWLDataProperty owlDataProperty) {

        String propertyName = owlDataProperty.getIRI().getFragment();

        if (options.getPrefixMode() && propertyName != null) {
            propertyName = getInitialLetterAsUpperCase(propertyName);

            OWLOntology classOntology = getParentOntology(owlDataProperty.getIRI());
            String classOntologyIRI;
            if (classOntology != null) {
                classOntologyIRI = classOntology.getOntologyID().getOntologyIRI().toString();
            } else {
                classOntologyIRI = owlOntology.getOntologyID().getOntologyIRI().toString();
            }
            String namePrefix = prefixMap.get(classOntologyIRI);
            if (namePrefix != null) {
                namePrefix = getInitialLetterAsUpperCase(namePrefix);
                propertyName = getInitialLetterAsUpperCase(propertyName);
                propertyName = namePrefix + "_" + propertyName;
            }
        }
        return propertyName;
    }

    /**
     * Prints package statement
     * 
     * @param printWriter
     */
    private void printInterfacePackageStatement(PrintWriter printWriter) {
        if (options.getPackage() != null) {
            printWriter.println("package " + options.getPackage() + ";");
            printWriter.println();
        }
    }

    private List<OWLObjectProperty> getClassObjectProperties(OWLClass owlClass) {
        List<OWLObjectProperty> owlObjectProperties = new ArrayList<OWLObjectProperty>();

        for (OWLObjectProperty owlObjectProperty : objectProperties) {
            Set<OWLClassExpression> owlClassExpressions = owlObjectProperty.getDomains(allOwlOntologies);

            for (OWLClassExpression owlClassExpression : owlClassExpressions) {
            	if (!owlClassExpression.isAnonymous()) {
            		OWLClass owlCls = owlClassExpression.asOWLClass();
            		if (owlClass.getIRI().toString().trim().equals(owlCls.getIRI().toString().trim())) {
            			owlObjectProperties.add(owlObjectProperty);
            			break;
            		}
            	}
            }
        }
        Set<OWLClassExpression> sc = owlClass.getSuperClasses(allOwlOntologies);
        for (OWLClassExpression owlClassExpression : sc) {
            if (owlClassExpression.isAnonymous()) {
                Set<OWLObjectProperty> property = owlClassExpression.getObjectPropertiesInSignature();
                for (OWLObjectProperty owlObjectProperty : property) {
                    if (!owlObjectProperties.contains(owlObjectProperty)) {
                        owlObjectProperties.add(owlObjectProperty);
                    }
                }
            }
        }
        
       Set<OWLClassExpression> eqClasses =  owlClass.getEquivalentClasses(allOwlOntologies);
        for (OWLClassExpression owlClassExpression : eqClasses) {
            if (owlClassExpression.isAnonymous()) {
                Set<OWLObjectProperty> property = owlClassExpression.getObjectPropertiesInSignature();
                for (OWLObjectProperty owlObjectProperty : property) {
                    if (!owlObjectProperties.contains(owlObjectProperty)) {
                        owlObjectProperties.add(owlObjectProperty);
                    }
                }
            }
        }
        
        return owlObjectProperties;
    }

    private List<OWLDataProperty> getClassDataProperties(OWLClass owlClass) {
        List<OWLDataProperty> owlDataProperties = new ArrayList<OWLDataProperty>();

        for (OWLDataProperty owlDataProperty : dataProperties) {
            Set<OWLClassExpression> owlClassExpressions = owlDataProperty.getDomains(allOwlOntologies);

            for (OWLClassExpression owlClassExpression : owlClassExpressions) {
            	if (!owlClassExpression.isAnonymous()) {
            		OWLClass owlClassToCompare = owlClassExpression.asOWLClass();
            		if (owlClass.getIRI().toString().trim().equals(owlClassToCompare.getIRI().toString().trim())) {
            			owlDataProperties.add(owlDataProperty);
            			break;
            		}
            	}
            }
        }
        return owlDataProperties;
    }

    private void addImportJavaUtilCode(PrintWriter printWriter, List<OWLDataProperty> owlDataProperties,
            List<OWLObjectProperty> owlObjectProperties) {
        for (Iterator iterator = owlDataProperties.iterator(); iterator.hasNext();) {
            OWLDataProperty owlDataProperty = (OWLDataProperty) iterator.next();
            if (!owlDataProperty.isFunctional(allOwlOntologies)) {
                printWriter.println("import java.util.*;");
                printWriter.println();
                return;
            }
        }
        for (Iterator iterator = owlObjectProperties.iterator(); iterator.hasNext();) {
            OWLObjectProperty owlObjectProperty = (OWLObjectProperty) iterator.next();
            if (!owlObjectProperty.isFunctional(allOwlOntologies)) {
                printWriter.println("import java.util.*;");
                printWriter.println();
                return;
            }
        }
    }

    private File getInterfaceFile(String name) {
        String pack = options.getPackage();
        if (pack != null) {
            pack = pack.replace('.', '/') + "/";
        } else {
            pack = "";
        }
        return new File(options.getOutputFolder(), pack + name + ".java");
    }

    private List<OWLClass> getClassesList(Node<OWLClass> topNode) {
        Node<OWLClass> unsatisfableClasses = reasoner.getUnsatisfiableClasses();

        List<OWLClass> owlClassList = new ArrayList<OWLClass>();
        classesNodeList = new ArrayList<Node<OWLClass>>();
        getSubClasses(topNode);

        for (Iterator iterator = classesNodeList.iterator(); iterator.hasNext();) {

            Node<OWLClass> nodeClasses = (Node<OWLClass>) iterator.next();
            Set<OWLClass> entities = nodeClasses.getEntities();
            for (Iterator iterator2 = entities.iterator(); iterator2.hasNext();) {
                OWLClass owlClass = (OWLClass) iterator2.next();
                if (!owlClassList.contains(owlClass) && !owlClass.isBuiltIn()
                        && !unsatisfableClasses.contains(owlClass)) {
                    owlClassList.add(owlClass);
                }
            }
        }
        return owlClassList;
    }

    private void getSubClasses(Node<OWLClass> parent) {

        if (parent.isBottomNode()) {
            return;
        }

        parent.getEntities();
        for (Node<OWLClass> child : reasoner.getSubClasses(parent.getRepresentativeElement(), true)) {
            if (!classesNodeList.contains(child)) {
                classesNodeList.add(child);
                getSubClasses(child);
            }
        }

    }

    private String getInterfaceExtendsCode(OWLClass owlClass) {
        String str = " extends ";
        String base = getBaseInterface(owlClass);
        if (base == null) {
            return str + "OWLIndividual";
        } else {
            return str + base;
        }
    }

    private void createImplementation(OWLClass owlClass) throws IOException {
        String implName = getImplementationNamePossiblyAbstract(owlClass);

        File file = getImplementationFile(implName);
        FileWriter fileWriter = new FileWriter(file);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printImplementationCode(implName, owlClass, printWriter);
        fileWriter.close();

        if (options.getAbstractMode()) {
            createUserImplementation(owlClass);
        }
    }

    private String getImplementationName(OWLClass owlClass) {
        return "Default" + getInterfaceName(owlClass);
    }

    private String getImplementationNamePossiblyAbstract(OWLClass owlClass) {
        return "Default" + getInterfaceNamePossiblyAbstract(owlClass);
    }

    private File getImplementationFile(String implName) {
        String pack = options.getPackage();
        if (pack != null) {
            pack = pack.replace('.', '/') + "/";
        } else {
            pack = "";
        }
        return new File(options.getOutputFolder(), pack + "impl/" + implName + ".java");
    }

    private void createUserInterface(OWLClass owlClass) throws IOException {
        String userInterfaceName = getInterfaceName(owlClass);
        File file = getInterfaceFile(userInterfaceName);
        if (!file.exists()) {
            FileWriter fileWriter = new FileWriter(file);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printUserInterfaceCode(userInterfaceName, owlClass, printWriter);
            fileWriter.close();
        }
    }

    private void printUserInterfaceCode(String userInterfaceName, OWLClass owlClass, PrintWriter printWriter) {
        printInterfacePackageStatement(printWriter);
        printWriter.println("public interface " + getInterfaceName(owlClass) + " extends "
                + getInterfaceNamePossiblyAbstract(owlClass) + " {");
        printWriter.println("}");

    }

    private void printImplementationCode(String implName, OWLClass owlClass, PrintWriter printWriter) {

        printImplementationPackageStatement(printWriter);
        printWriter.println();
        List<OWLObjectProperty> owlObjectProperties = getClassObjectProperties(owlClass);
        List<OWLDataProperty> owlDataProperties = getClassDataProperties(owlClass);
        String pack = options.getPackage();
        if (pack != null) {
            printWriter.println("import " + pack + "." + getInterfaceNamePossiblyAbstract(owlClass) + ";");
            printWriter.println("import " + pack + ".*;");
            printWriter.println();
        }

        printWriter.println("import java.util.*;");
        printWriter.println();
        printWriter.println("import org.protege.editor.owl.codegeneration.AbstractCodeGeneratorIndividual;");
        printWriter.println("import org.semanticweb.owlapi.model.*;");

        printWriter.println();
        printWriter.println("/**");
        printWriter.println(" * Generated by Protege (http://protege.stanford.edu).");
        printWriter.println(" * Source Class: " + owlClass.getIRI().toString());
        printWriter.println(" *");
        printWriter.println(" * @version generated on " + new Date());
        printWriter.println(" */");

        printWriter.println();
        printWriter.println("public class " + implName + getImplementationExtendsCode(owlClass));
        printWriter.println("         implements " + getInterfaceNamePossiblyAbstract(owlClass) + " {");

        printConstructors(printWriter, implName);

        for (Iterator iterator = owlObjectProperties.iterator(); iterator.hasNext();) {
            OWLObjectProperty owlObjectProperty = (OWLObjectProperty) iterator.next();
            printImplementationObjectPropertyCode(owlObjectProperty, printWriter);
            printWriter.println();
        }

        for (Iterator iterator = owlDataProperties.iterator(); iterator.hasNext();) {
            OWLDataProperty owlDataProperty = (OWLDataProperty) iterator.next();
            printImplementationDataPropertyCode(owlDataProperty, printWriter);
        }

        printWriter.println("    public void delete(){");
        printWriter.println("        deleteIndividual();");
        printWriter.println("    }");
        printWriter.println();
        printWriter.println("}");

    }

    /**
     * @param printWriter
     */
    private void printImplementationPackageStatement(PrintWriter printWriter) {
        if (options.getPackage() != null) {
            printWriter.println("package " + options.getPackage() + ".impl;");
        } else {
            printWriter.println("package impl;");
        }
    }

    private String getImplementationExtendsCode(OWLClass owlClass) {
        String str = " extends ";
        String base = getBaseImplementation(owlClass);
        if (base == null) {
            return str + JavaCodeGeneratorConstants.ABSTRACT_CODE_GENERATOR_INDIVIDUAL_CLASS;
        } else {
            return str + base;
        }
    }

    private void printConstructors(PrintWriter printWriter, String implementationName) {
        printWriter.println();
        printWriter.println("    public " + implementationName
                + "(OWLDataFactory owlDataFactory, IRI iri, OWLOntology owlOntology) {");
        printWriter.println("        super(owlDataFactory, iri, owlOntology);");
        printWriter.println("    }");
        printWriter.println();
    }

    private void printImplementationObjectPropertyCode(OWLObjectProperty owlObjectProperty, PrintWriter printWriter) {
        String propertyName = getObjectPropertyName(owlObjectProperty);
        String propertyNameUpperCase = getInitialLetterAsUpperCase(propertyName);
        String getPropertyFunctionName = "get" + propertyNameUpperCase + "Property()";
        String objectPropertyRange = getObjectPropertyRange(owlObjectProperty, false);
        boolean isFunctional = owlObjectProperty.isFunctional(allOwlOntologies);

        printWriter.println();
        printWriter.println("    // Property " + owlObjectProperty.getIRI());
        printWriter.println();
        printWriter.println("    public OWLObjectProperty " + getPropertyFunctionName + " {");
        printWriter.println("        final String iriString = \"" + owlObjectProperty.getIRI() + "\";");
        printWriter.println("        final IRI iri = IRI.create(iriString);");
        printWriter.println("        return getOWLDataFactory().getOWLObjectProperty(iri);");
        printWriter.println("    }");

        printWriter.println();

        printImplObjectPopertyGetMethod(owlObjectProperty, printWriter, propertyNameUpperCase, getPropertyFunctionName,
                objectPropertyRange, isFunctional);

        printWriter.println();
        printWriter.println();
        printWriter.println("    public boolean has" + propertyNameUpperCase + "() {");
        printWriter.println("        Set<OWLIndividual> values = getObjectPropertyValues(" + getPropertyFunctionName
                + ", getOwlOntology());");
        printWriter.println("        return (values == null || values.isEmpty()) ? false : true;");
        printWriter.println("    }");

        if (!isFunctional) {
            Set<OWLClassExpression> oClassExpressions = owlObjectProperty.getRanges(allOwlOntologies);
            String objPropertyJavaName = getObjectPropertyJavaName(oClassExpressions);

            printWriter.println();
            printWriter.println("    public " + PropertyConstants.JAVA_UTIL_ITERATOR + " list" + propertyNameUpperCase
                    + "() {");
            printWriter.println("        return get" + propertyNameUpperCase + "().iterator();");
            printWriter.println("    }");

            printWriter.println();
            printWriter.println("    public void add" + propertyNameUpperCase + "(" + objPropertyJavaName + " new"
                    + propertyNameUpperCase + ") {");
            printWriter
                    .println("        OWLObjectPropertyAssertionAxiom axiom = getOWLDataFactory().getOWLObjectPropertyAssertionAxiom( "
                            + getPropertyFunctionName
                            + ", this, (OWLIndividual)"
                            + " new"
                            + propertyNameUpperCase
                            + " );");
            printWriter.println("        getOwlOntology().getOWLOntologyManager().addAxiom( getOwlOntology(), axiom);");
            printWriter.println("    }");
            printWriter.println();
            printWriter.println("    public void remove" + propertyNameUpperCase + "(" + objPropertyJavaName + " old"
                    + propertyNameUpperCase + ") {");
            printWriter.println("        removeObjectPropertyValue( " + getPropertyFunctionName + ", (OWLNamedIndividual) old" + propertyNameUpperCase+");");
            printWriter.println("    }");

            printWriter.println();
            printWriter.println("    public void set" + propertyNameUpperCase + "( " + objectPropertyRange + " new"
                    + propertyNameUpperCase + "List ) {");
            printWriter.println("        OWLObjectProperty property = " + getPropertyFunctionName + ";");
            printWriter.println("        " + objectPropertyRange + " prevValues = get" + propertyNameUpperCase + "();");
            printWriter.println("        for (" + objPropertyJavaName + " value : prevValues) {");
            printWriter.println("            removeObjectPropertyValue( property, (OWLNamedIndividual) value);");
            printWriter.println("        }");

            printWriter.println("        for (" + objPropertyJavaName + " element : " + " new" + propertyNameUpperCase
                    + "List" + ") {");
            printWriter
                    .println("            OWLObjectPropertyAssertionAxiom axiom = getOWLDataFactory().getOWLObjectPropertyAssertionAxiom( "
                            + getPropertyFunctionName + ", this, (OWLIndividual)" + " element);");
            printWriter
                    .println("            getOwlOntology().getOWLOntologyManager().addAxiom(getOwlOntology(), axiom);");
            printWriter.println("        }");
            printWriter.println("    }");

        } else {

            printWriter.println();
            printWriter.println("    public void set" + propertyNameUpperCase + "(" + objectPropertyRange + " new"
                    + propertyNameUpperCase + ") {");
            printWriter.println("        OWLObjectProperty property = " + getPropertyFunctionName + ";");
            printWriter.println("        " + objectPropertyRange + " prevValue = get" + propertyNameUpperCase + "();");
            printWriter.println("        removeObjectPropertyValue( property, (OWLNamedIndividual) prevValue);");
            printWriter
                    .println("        OWLObjectPropertyAssertionAxiom axiom = getOWLDataFactory().getOWLObjectPropertyAssertionAxiom( "
                            + getPropertyFunctionName + ", this, " + "new" + propertyNameUpperCase + ");");
            printWriter.println("        getOwlOntology().getOWLOntologyManager().addAxiom(getOwlOntology(), axiom);");
            printWriter.println("    }");

        }
    }

    /** Prints 
     * @param owlObjectProperty
     * @param printWriter
     * @param propertyNameUpperCase
     * @param getPropertyFunctionName
     * @param objectPropertyRange
     * @param isFunctional
     */
    private void printImplObjectPopertyGetMethod(OWLObjectProperty owlObjectProperty, PrintWriter printWriter,
            String propertyNameUpperCase, String getPropertyFunctionName, String objectPropertyRange,
            boolean isFunctional) {
        printWriter.println("    public " + objectPropertyRange + " get" + propertyNameUpperCase + "() {");
        if (!isFunctional) {
            Set<OWLClassExpression> oClassExpressions = owlObjectProperty.getRanges(allOwlOntologies);
            String objectPropertyType = getObjectPropertyJavaName(oClassExpressions);

            printWriter.println("        Set<" + objectPropertyType + "> propertyValues = new HashSet<"
                    + objectPropertyType + ">();");
            printWriter.println("        Set<OWLIndividual> values = getObjectPropertyValues("
                    + getPropertyFunctionName + ", getOwlOntology());");
            printWriter.println("        if(values ==null || values.isEmpty()){");
            printWriter.println("            return null;");
            printWriter.println("        }");
            printWriter.println("        for (OWLIndividual owlIndividual : values) {");
            if (objectPropertyType.equals(PropertyConstants.JAVA_LANG_OBJECT)) {
                printWriter.println("  propertyValues.add(owlIndividual);");
            } else {
                printWriter.println("            propertyValues.add(new Default" + objectPropertyType
                        + "(getOWLDataFactory(), owlIndividual.asOWLNamedIndividual().getIRI(), getOwlOntology()));");
            }
            printWriter.println("        }");
            printWriter.println("        return propertyValues;");

        } else {
            printWriter.println("        Set<OWLIndividual> values = getObjectPropertyValues("
                    + getPropertyFunctionName + ", getOwlOntology());");
            printWriter.println("        if (values == null || values.isEmpty()) {");
            printWriter.println("            return null;");
            printWriter.println("        }");
            printWriter.println("        for (OWLIndividual owlIndividual : values) {");
            printWriter.println("            return new Default" + objectPropertyRange
                    + "(getOWLDataFactory(), owlIndividual.asOWLNamedIndividual().getIRI(), getOwlOntology());");
            printWriter.println("        }");
            printWriter.println("        return null;");
        }
        printWriter.println("    }");
    }

    /** 
     * Creates implementation when 'create abstract base file' option is true
     * @param owlClass
     * @throws IOException
     */
    private void createUserImplementation(OWLClass owlClass) throws IOException {
        String implName = getImplementationName(owlClass);
        File file = getImplementationFile(implName);
        if (!file.exists()) {
            FileWriter fileWriter = new FileWriter(file);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printUserImplementationCode(implName, owlClass, printWriter);
            fileWriter.close();
        }
    }

    /**Prints implementation code when 'create abstract base file' option is true
     * @param implName
     * @param owlClass
     * @param printWriter
     */
    private void printUserImplementationCode(String implName, OWLClass owlClass, PrintWriter printWriter) {
        printImplementationPackageStatement(printWriter);

        String pack = options.getPackage();
        if (pack != null) {
            printWriter.println("import " + pack + ".*;");
            printWriter.println();
        }
        printWriter.println("import org.semanticweb.owlapi.model.*;");
        printWriter.println();
        printWriter.println("public class " + implName + " extends " + getImplementationNamePossiblyAbstract(owlClass));
        printWriter.println("         implements " + getInterfaceName(owlClass) + " {");
        printConstructors(printWriter, implName);
        printWriter.println("}");

    }

    /** Prints code for data properties for OWLClass implementation
     * @param owlDataProperty
     * @param printWriter
     */
    private void printImplementationDataPropertyCode(OWLDataProperty owlDataProperty, PrintWriter printWriter) {

        String propertyName = getDataPropertyName(owlDataProperty);
        String propertyNameUpperCase = getInitialLetterAsUpperCase(propertyName);
        String getPropertyFunctionName = "get" + propertyNameUpperCase + "Property()";

        Set<OWLDataRange> owlDataRanges = owlDataProperty.getRanges(allOwlOntologies);
        String dataPropertyJavaName = getDataPropertyJavaName(owlDataRanges);

        boolean isDataTypeBasic = isDataTypeBasic(owlDataProperty);

        boolean isFunctional = owlDataProperty.isFunctional(allOwlOntologies);
        printWriter.println();
        printWriter.println();
        printWriter.println("    // Property " + owlDataProperty.getIRI());
        printWriter.println();
        printWriter.println("    public " + "OWLDataProperty " + getPropertyFunctionName + " {");
        printWriter.println("        final String iriString = \"" + owlDataProperty.getIRI() + "\";");
        printWriter.println("        final IRI iri = IRI.create(iriString);");
        printWriter.println("        return getOWLDataFactory().getOWLDataProperty(iri);");
        printWriter.println("    }");
        printWriter.println();

        printImplDataPropertyGetterCode(owlDataProperty, printWriter, propertyNameUpperCase, getPropertyFunctionName,
                dataPropertyJavaName, isFunctional);

        printWriter.println("    public boolean has" + propertyNameUpperCase + "(){");
        printWriter.println("        " + getDataPropertyRange(owlDataProperty) + " value = get" + propertyNameUpperCase
                + "();");
        if (isFunctional) {
            printWriter.println("        return ( value == null )? false : true;");
        } else {
            printWriter.println("        return ( value == null || value.isEmpty() )? false : true;");
        }
        printWriter.println("    }");

        if (!owlDataProperty.isFunctional(allOwlOntologies)) {
            printWriter.println();
            printWriter.println("    public " + PropertyConstants.JAVA_UTIL_ITERATOR + " list" + propertyNameUpperCase
                    + "() {");
            printWriter.println("        return get" + propertyNameUpperCase + "().iterator();");
            printWriter.println("    }");
            printWriter.println();
            printWriter.println("    public void add" + propertyNameUpperCase + "("
                    + getDataPropertyJavaName(owlDataRanges) + " new" + propertyNameUpperCase + "){");
            if (isDataTypeBasic) {
                printWriter.println("        OWLLiteral literal = getOWLDataFactory().getOWLLiteral( new"
                        + propertyNameUpperCase + ");");
            } else {
                printWriter.println("        OWLLiteral literal = getOWLDataFactory().getOWLLiteral( new"
                        + propertyNameUpperCase + ".toString(), \"\");");
            }
            printWriter
                    .println("        if (!doesPropertyContainsLiteral(" + getPropertyFunctionName + ", literal)) {");
            printWriter
                    .println("            OWLDataPropertyAssertionAxiom axiom = getOWLDataFactory().getOWLDataPropertyAssertionAxiom(");
            printWriter.println("                " + getPropertyFunctionName + ", this, literal);");
            printWriter
                    .println("            getOwlOntology().getOWLOntologyManager().addAxiom(getOwlOntology(), axiom);");
            printWriter.println("        }");
            printWriter.println("    }");
            printWriter.println();
            printWriter.println("    public void remove" + propertyNameUpperCase + "("
                    + getDataPropertyJavaName(owlDataRanges) + " old" + propertyNameUpperCase + "){");
            if (isDataTypeBasic) {
                printWriter.println("        OWLLiteral literal = getOWLDataFactory().getOWLLiteral( old"
                        + propertyNameUpperCase + ");");
            } else {
                printWriter.println("        OWLLiteral literal = getOWLDataFactory().getOWLLiteral( old"
                        + propertyNameUpperCase + ".toString(),\"\");");
            }
            printWriter.println("        removeDataPropertyValue(" + getPropertyFunctionName + ", literal);");
            printWriter.println("    }");

        }
        printWriter.println();

        printImplDataPropertySetterCode(owlDataProperty, printWriter, propertyNameUpperCase, getPropertyFunctionName,
                dataPropertyJavaName, isDataTypeBasic, isFunctional);
        printWriter.println("    }");

    }

    /**
     * Prints getter code for the OWLDataProperty for a particular OWLClass
     * @param owlDataProperty
     * @param printWriter
     * @param propertyNameUpperCase
     * @param getPropertyFunctionName
     * @param dataPropertyJavaName
     * @param isFunctional
     */
    private void printImplDataPropertyGetterCode(OWLDataProperty owlDataProperty, PrintWriter printWriter,
            String propertyNameUpperCase, String getPropertyFunctionName, String dataPropertyJavaName,
            boolean isFunctional) {
        printWriter.println("    public " + getDataPropertyRange(owlDataProperty) + " get" + propertyNameUpperCase
                + "() {");

        if (isFunctional) {

            printWriter.println("        Set<OWLLiteral> propertyValues = getDataPropertyValues( "
                    + getPropertyFunctionName + ", getOwlOntology());");
            printWriter.println("        for (OWLLiteral owlLiteral : propertyValues) {");
            if (dataPropertyJavaName.equalsIgnoreCase(PropertyConstants.JAVA_LANG_OBJECT)) {
                printWriter.println("            return (" + dataPropertyJavaName + ") owlLiteral.getLiteral();");
            } else {
                printWriter.println("            return new " + dataPropertyJavaName + "( owlLiteral.getLiteral());");
            }
            printWriter.println("        }");
            printWriter.println("        return null;");
        } else {
            printWriter.println("        Set<OWLLiteral> propertyValues = getDataPropertyValues( "
                    + getPropertyFunctionName + ", getOwlOntology());");
            printWriter.println("        Set<" + dataPropertyJavaName + "> values = new HashSet<"
                    + dataPropertyJavaName + ">();");
            printWriter.println("        for (OWLLiteral owlLiteral : propertyValues) {");
            if (dataPropertyJavaName.equalsIgnoreCase(PropertyConstants.JAVA_LANG_OBJECT)) {
                printWriter.println("            values.add( (" + dataPropertyJavaName + ")owlLiteral.getLiteral());");
            } else {
                printWriter.println("            values.add( new " + dataPropertyJavaName
                        + "( owlLiteral.getLiteral()));");
            }
            printWriter.println("        }");
            printWriter.println("        return values;");
        }
        printWriter.println("    }");
        printWriter.println();
    }

    /** Prints setter code for the OWLDataProperty for a particular OWLClass
     * @param owlDataProperty
     * @param printWriter
     * @param propertyNameUpperCase
     * @param getPropertyFunctionName
     * @param dataPropertyJavaName
     * @param isDataTypeBasic
     * @param isFunctional
     */
    private void printImplDataPropertySetterCode(OWLDataProperty owlDataProperty, PrintWriter printWriter,
            String propertyNameUpperCase, String getPropertyFunctionName, String dataPropertyJavaName,
            boolean isDataTypeBasic, boolean isFunctional) {
        if (isFunctional) {
            printWriter.println("    public void set" + propertyNameUpperCase + "("
                    + getDataPropertyRange(owlDataProperty) + " new" + propertyNameUpperCase + "){");
            printWriter.println("        " + getDataPropertyRange(owlDataProperty) + " prevValue = get"
                    + propertyNameUpperCase + "()" + ";");
            printWriter.println("        //Remove previous value/values");
            printWriter.println("        if (prevValue != null) {");
            if (isDataTypeBasic) {
                printWriter
                        .println("            OWLLiteral literalToRemove = getOWLDataFactory().getOWLLiteral(prevValue);");
                printWriter.println("            removeDataPropertyValue( "+getPropertyFunctionName+", literalToRemove);");
                printWriter.println("        }");
                printWriter.println("    OWLLiteral literal = getOWLDataFactory().getOWLLiteral((" + " new"
                        + propertyNameUpperCase + " == null) ? null : " + " new" + propertyNameUpperCase + ");");

            } else {
                printWriter
                        .println("            OWLLiteral literalToRemove = getOWLDataFactory().getOWLLiteral(prevValue.toString(), \"\");");
                printWriter.println("            removeDataPropertyValue( "+getPropertyFunctionName+", literalToRemove);");
                printWriter.println("        }");
                printWriter.println("    OWLLiteral literal = getOWLDataFactory().getOWLLiteral((" + " new"
                        + propertyNameUpperCase + " == null) ? null : " + " new" + propertyNameUpperCase
                        + ".toString(), \"\");");

            }
            printWriter.println("    setDataProperty(" + getPropertyFunctionName + ", literal);");

        } else {
            printWriter.println("    public void set" + propertyNameUpperCase + "("
                    + getDataPropertyRange(owlDataProperty) + " new" + propertyNameUpperCase + "List ){");
            printWriter.println("        " + getDataPropertyRange(owlDataProperty) + " prevValueList = get"
                    + propertyNameUpperCase + "();");
            printWriter.println("        if (prevValueList != null) {");
            printWriter.println("            for (" + dataPropertyJavaName + " prevValue : prevValueList) {");
            if (isDataTypeBasic) {
                printWriter
                        .println("                OWLLiteral literalToRemove = getOWLDataFactory().getOWLLiteral(prevValue);");
            } else {
                printWriter
                        .println("                OWLLiteral literalToRemove = getOWLDataFactory().getOWLLiteral(prevValue.toString(), \"\");");
            }

            printWriter.println("                removeDataPropertyValue( "+getPropertyFunctionName+", literalToRemove );");
            printWriter.println("            }");
            printWriter.println("        }");
            printWriter.println("        for (" + dataPropertyJavaName + " value : new" + propertyNameUpperCase
                    + "List ) {");
            if (isDataTypeBasic) {
                printWriter.println("            OWLLiteral literal = getOWLDataFactory().getOWLLiteral(value);");
            } else {
                printWriter
                        .println("            OWLLiteral literal = getOWLDataFactory().getOWLLiteral(value.toString(), \"\");");
            }
            printWriter.println("            setDataProperty(" + getPropertyFunctionName + ", literal);");
            printWriter.println("        }");

        }
    }

    /** Determines whether the data type for the OWLDataProperty is basic or not
     * @param owlDataProperty
     * @return
     */
    private boolean isDataTypeBasic(OWLDataProperty owlDataProperty) {
        Set<OWLDataRange> ranges = owlDataProperty.asOWLDataProperty().getRanges(allOwlOntologies);
        if (ranges == null || ranges.isEmpty() || ranges.size() > 1) {
            return false;
        }
        for (OWLDataRange owlDataRange : ranges) {
            OWLDatatype owlDatatype = owlDataRange.asOWLDatatype();
            if (owlDatatype.isBoolean() || owlDatatype.isDouble() || owlDatatype.isFloat() || owlDatatype.isInteger()
                    || owlDatatype.isString()) {
                return true;
            }
        }
        return false;
    }

    /** Initilizes the vocabulary code generation 
     * @param owlClassList
     * @throws IOException
     */
    private void printVocabularyCode(List<OWLClass> owlClassList) throws IOException {
        createVocabularyClassFile();
        printVocabularyInitialCode();

        vocabularyPrintWriter
                .println("    private static final OWLDataFactory factory = OWLManager.createOWLOntologyManager().getOWLDataFactory();");
        vocabularyPrintWriter.println();

        for (Iterator iterator = owlClassList.iterator(); iterator.hasNext();) {
            OWLClass owlClass = (OWLClass) iterator.next();
            printClassVocabularyCode(owlClass);
        }

        for (OWLObjectProperty owlObjectProperty : objectProperties) {
            String propertyName = getObjectPropertyName(owlObjectProperty);
            vocabularyPrintWriter
                    .println("    public static final OWLObjectProperty " + propertyName.toUpperCase()
                            + " = factory.getOWLObjectProperty(IRI.create(\"" + owlObjectProperty.getIRI().toString()
                            + "\"));");
            vocabularyPrintWriter.println();
        }

        for (OWLDataProperty owlDataProperty : dataProperties) {
            String propertyName = getDataPropertyName(owlDataProperty);
            vocabularyPrintWriter.println("    public static final OWLDataProperty " + propertyName.toUpperCase()
                    + " = factory.getOWLDataProperty(IRI.create(\"" + owlDataProperty.getIRI().toString() + "\"));");
            vocabularyPrintWriter.println();
        }

        printVocabularyEndCode();
    }

    /**Creates file for Vocabulary class
     * @throws IOException
     */
    private void createVocabularyClassFile() throws IOException {
        File vocabularyFile = getInterfaceFile(JavaCodeGeneratorConstants.VOCABULARY_CLASS_NAME);
        vocabularyfileWriter = new FileWriter(vocabularyFile);
        vocabularyPrintWriter = new PrintWriter(vocabularyfileWriter);

    }

    /**
     * Prints the initial code for Vocabulary class
     */
    private void printVocabularyInitialCode() {
        printInterfacePackageStatement(vocabularyPrintWriter);
        vocabularyPrintWriter.println("import org.semanticweb.owlapi.apibinding.OWLManager;");
        vocabularyPrintWriter.println("import org.semanticweb.owlapi.model.*;");
        vocabularyPrintWriter.println();
        vocabularyPrintWriter.println("/**");
        vocabularyPrintWriter.println(" * Generated by Protege (http://protege.stanford.edu).");
        vocabularyPrintWriter.println(" * Source Class: Vocabulary");
        vocabularyPrintWriter.println(" *");
        vocabularyPrintWriter.println(" * @version generated on " + new Date());
        vocabularyPrintWriter.println(" */");
        vocabularyPrintWriter.println();
        vocabularyPrintWriter.println("public class " + JavaCodeGeneratorConstants.VOCABULARY_CLASS_NAME + " {");
    }

    /** Prints the Vocabulary code for the provided OWLClass 
     * @param owlClass
     */
    private void printClassVocabularyCode(OWLClass owlClass) {
        String className = getInterfaceName(owlClass);
        vocabularyPrintWriter.println("    public static final OWLClass " + className.toUpperCase()
                + " = factory.getOWLClass(IRI.create(\"" + owlClass.getIRI().toString() + "\"));");
        vocabularyPrintWriter.println();
    }

    /**Prints the terminating code for Vocabulary code
     * @throws IOException
     */
    private void printVocabularyEndCode() throws IOException {
        vocabularyPrintWriter.println(" }");
        vocabularyfileWriter.close();

    }

    /** Initializes the code generation for factory classes 
     * @param owlClassList
     * @throws IOException
     */
    private void printFactoryClassCode(List<OWLClass> owlClassList) throws IOException {
        FileWriter factoryFileWriter = null;
        PrintWriter factoryPrintWriter = null;
        File factoryFile = getInterfaceFile(options.getFactoryClassName());
        factoryFileWriter = new FileWriter(factoryFile);
        factoryPrintWriter = new PrintWriter(factoryFileWriter);
        printFactoryInitialCode(factoryPrintWriter);

        for (Iterator iterator = owlClassList.iterator(); iterator.hasNext();) {
            OWLClass owlClass = (OWLClass) iterator.next();
            printFactoryCodeForClass(owlClass, factoryPrintWriter);
        }
        printFactoryClassEndCode(factoryPrintWriter, factoryFileWriter);
    }

    /**Prints the initial code for factory class
     * @param factoryPrintWriter
     */
    private void printFactoryInitialCode(PrintWriter factoryPrintWriter) {
        printInterfacePackageStatement(factoryPrintWriter);
        factoryPrintWriter.println("import java.util.*;");
        factoryPrintWriter.println("import org.semanticweb.owlapi.model.*;");
        factoryPrintWriter.println();
        factoryPrintWriter.println();
        if (options.getPackage() != null) {
            factoryPrintWriter.println("import static " + options.getPackage() + "."
                    + JavaCodeGeneratorConstants.VOCABULARY_CLASS_NAME + ".*;");
            factoryPrintWriter.println("import " + options.getPackage() + ".impl.*;");
        } else {
            factoryPrintWriter.println("import static " + JavaCodeGeneratorConstants.VOCABULARY_CLASS_NAME + ".*;");
            factoryPrintWriter.println("import impl.*;");
        }

        factoryPrintWriter.println();
        factoryPrintWriter.println("/**");
        factoryPrintWriter.println(" * Generated by Protege (http://protege.stanford.edu).");
        factoryPrintWriter.println(" * Source Class: Factory");
        factoryPrintWriter.println(" *");
        factoryPrintWriter.println(" * @version generated on " + new Date());
        factoryPrintWriter.println(" */");
        factoryPrintWriter.println();
        factoryPrintWriter.println("public class " + options.getFactoryClassName().trim() + " {");
        factoryPrintWriter.println();
        factoryPrintWriter.println("    private OWLOntology owlOntology;");
        factoryPrintWriter.println();
        factoryPrintWriter
                .println("    public " + options.getFactoryClassName().trim() + "(OWLOntology owlOntology) {");
        factoryPrintWriter.println("        this.owlOntology = owlOntology;");
        factoryPrintWriter.println("    }");

    }

    /** Prints the factory code for the provided OWLClass to the PrintStream
     * @param owlClass
     * @param factoryPrintWriter
     */
    private void printFactoryCodeForClass(OWLClass owlClass, PrintWriter factoryPrintWriter) {
        String implName = getImplementationName(owlClass);
        String className = getInterfaceName(owlClass);

        factoryPrintWriter.println("    public " + className + " create" + className + "(String name) {");
        factoryPrintWriter
                .println("        IRI iri = IRI.create(owlOntology.getOntologyID().getOntologyIRI().toString() + \"#\" + name);");
        factoryPrintWriter.println("        " + implName + " entity = new " + implName
                + "(owlOntology.getOWLOntologyManager().getOWLDataFactory(), iri, owlOntology);");
        factoryPrintWriter
                .println("        OWLClassAssertionAxiom axiom = owlOntology.getOWLOntologyManager().getOWLDataFactory().getOWLClassAssertionAxiom("
                        + className.toUpperCase() + ", entity); ");
        factoryPrintWriter.println("        owlOntology.getOWLOntologyManager().addAxiom(owlOntology, axiom);");
        factoryPrintWriter.println("        return entity;");
        factoryPrintWriter.println("    }");
        factoryPrintWriter.println();

        factoryPrintWriter.println("    public " + className + " get" + className + "(String name) {");
        factoryPrintWriter.println("        Set<OWLIndividual> individuals =" + className.toUpperCase()
                + ".getIndividuals(owlOntology);");
        factoryPrintWriter.println("        if(individuals == null) {");
        factoryPrintWriter.println("            return null;");
        factoryPrintWriter.println("        }");
        factoryPrintWriter.println("        for (OWLIndividual owlIndividual : individuals) {");
        factoryPrintWriter
                .println("            String fragment = owlIndividual.asOWLNamedIndividual().getIRI().getFragment();");
        factoryPrintWriter.println("            if(fragment.trim().equals(name.trim())){");
        factoryPrintWriter
                .println("                return  new Default"
                        + className
                        + "(owlOntology.getOWLOntologyManager().getOWLDataFactory(), owlIndividual.asOWLNamedIndividual().getIRI(), owlOntology);");
        factoryPrintWriter.println("            }");
        factoryPrintWriter.println("        }");
        factoryPrintWriter.println("        return null;");
        factoryPrintWriter.println("    }");
        factoryPrintWriter.println();

        factoryPrintWriter.println("    public Collection<" + className + "> getAll" + className + "Instance(){");
        factoryPrintWriter.println("        Collection<" + className + "> instances = new HashSet<" + className
                + ">();");
        factoryPrintWriter.println("        Set<OWLIndividual> individuals =" + className.toUpperCase()
                + ".getIndividuals(owlOntology);");
        factoryPrintWriter.println("        for (OWLIndividual owlIndividual : individuals) {");
        factoryPrintWriter
                .println("            instances.add(new Default"
                        + className
                        + "(owlOntology.getOWLOntologyManager().getOWLDataFactory(), owlIndividual.asOWLNamedIndividual().getIRI(), owlOntology));");
        factoryPrintWriter.println("        }");
        factoryPrintWriter.println("        return instances;");
        factoryPrintWriter.println("    }");
        factoryPrintWriter.println();
    }

    /** Prints the terminating code
     * @param factoryPrintWriter
     * @param factoryFileWriter
     * @throws IOException
     */
    private void printFactoryClassEndCode(PrintWriter factoryPrintWriter, FileWriter factoryFileWriter)
            throws IOException {
        factoryPrintWriter.println(" }");
        factoryFileWriter.close();
    }

    /** Returns base implementation of the provided OWLClass
     * @param owlClass
     * @return
     */
    private String getBaseImplementation(OWLClass owlClass) {
        String baseImplementationString = "";
        for (OWLClassExpression owlClassExpression : owlClass.getSuperClasses(allOwlOntologies)) {
            if (owlClassExpression.isAnonymous()) {
                continue;
            }
            OWLClass superClass = owlClassExpression.asOWLClass();
            if (superClass != null && !superClass.isTopEntity() && !superClass.isBuiltIn()) {
                if (baseImplementationString.equals("")) {
                    baseImplementationString = getImplementationName(superClass);
                } else {
                    return null;
                }
            }
        }
        if (baseImplementationString.equals("")) {
            return null;
        } else {
            return baseImplementationString;
        }
    }

    /** Returns base interface of the provided OWLClass
     * @param owlClass The OWLClass whose base interface is to be returned
     * @return
     */
    private String getBaseInterface(OWLClass owlClass) {
        String baseInterfaceString = "";
        for (OWLClassExpression owlClassExpression : owlClass.getSuperClasses(allOwlOntologies)) {
            if (owlClassExpression.isAnonymous()) {
                continue;
            }
            OWLClass superClass = owlClassExpression.asOWLClass();
            if (superClass != null && !superClass.isTopEntity() && !superClass.isBuiltIn()) {
                baseInterfaceString += (baseInterfaceString.equals("") ? "" : ", ") + getInterfaceName(superClass);
            }
        }
        if (baseInterfaceString.equals("")) {
            return null;
        } else {
            return baseInterfaceString;
        }
    }

    /** Returns the provided string with initial letter as capital
     * @param name
     * @return
     */
    public String getInitialLetterAsUpperCase(String name) {
        if (name == null) {
            return null;
        }
        if (name.length() > 1) {
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        } else {
            return name.toUpperCase();
        }
    }
}
