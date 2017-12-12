package info.cartograph;

import com.google.common.primitives.Ints;
import org.apache.commons.cli.*;
import org.jgrapht.Graph;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.DaoFilter;
import org.wikibrain.core.dao.LocalCategoryMemberDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.CategoryGraph;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.phrases.PhraseAnalyzer;

import java.io.*;
import java.util.Collection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by Andre Archer
 */
public class LabelCreator {

    public static void pageCategoryHashMap(Env env) throws ConfigurationException, DaoException, FileNotFoundException {
        // Configure the WikiBrain environment

        // Get the primary installed
        Language lang = env.getDefaultLanguage();

        ////////////////////////////////
        // Three components needed
        ////////////////////////////////

        // Component for basic information about articles
        LocalPageDao pageDao = env.getComponent(LocalPageDao.class);

        // Provide categories for an article (and vice versa), and generate the graph
        LocalCategoryMemberDao catDao = env.getComponent(LocalCategoryMemberDao.class);
        CategoryGraph graph = catDao.getGraph(lang);
        LinkedHashMap<Object, ArrayList<String>> pagecategoryHashMap = new LinkedHashMap<Object, ArrayList<String>>();
        String catName;
        String finName;
        for (LocalPage page : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            Collection<Integer> cats = catDao.getCategoryIds(page);
            if (cats != null) {
                for (int c : cats) {
                    catName = graph.getCategoryName(c);
                    if(!catName.equals("")){
                        System.out.println(page.getTitle() + "\t" + catName.split(":")[1]);
                    }
                }
            }
        }
    }

    public static void getCategoryParents(Env env) throws ConfigurationException, DaoException, FileNotFoundException{
            // Configure the WikiBrain environment


            // Get the primary installed
            Language lang = Language.getByLangCode("simple");

            ////////////////////////////////
            // Three components needed
            ////////////////////////////////

            // Component for basic information about articles
            LocalPageDao pageDao = env.getComponent(LocalPageDao.class);

            // Provide categories for an article (and vice versa), and generate the graph
            LocalCategoryMemberDao catDao = env.getComponent(LocalCategoryMemberDao.class);
            CategoryGraph graph = catDao.getGraph(lang);

            LinkedHashMap<String, ArrayList<String>> categoryHashMap = new LinkedHashMap<String, ArrayList<String>>();
            for (int cat : graph.catIds) {
                String childName = graph.getCategoryName(cat);
                String parentName;
                int[] parents = graph.getFamilyMembersCategories(cat, "parent");
                if(!childName.equals("")) {
                    for (int par : parents) {
                        parentName = graph.getCategoryName(par);
                        if(!parentName.equals("")) {
                            System.out.println(childName.split(":")[1] + "\t" + parentName.split(":")[1]);
                        }
                    }
                }
            }
        }




    /**
     *
     * @param lang
     * @param pageDao
     * @param catDao
     * @param T
     * @return
     * @throws ConfigurationException
     * @throws DaoException
     * @throws FileNotFoundException
     */
    public static LinkedHashMap<LocalPage, Map<Integer, Integer>> traverseByMaxMinPageRank(Language lang, LocalPageDao pageDao, LocalCategoryMemberDao catDao,boolean T) throws ConfigurationException, DaoException, FileNotFoundException{
        CategoryGraph graph = catDao.getGraph(lang);
        LinkedHashMap<LocalPage, Map<Integer, Integer>> catMap = new LinkedHashMap();
        for (LocalPage page : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            Collection<Integer> cats = catDao.getCategoryIds(page);

            if (cats != null) {

                Integer cArg = null;
                for (int c : cats) {
                    if (graph.catIdToIndex(c) >= 0) {
                        if(T) {
                            cArg =  ((cArg == null) || (graph.catCosts[graph.catIdToIndex(cArg)] < graph.catCosts[graph.catIdToIndex(c)])) ? c: cArg;
                        }else{
                            cArg =  ((cArg == null) || (graph.catCosts[graph.catIdToIndex(cArg)] > graph.catCosts[graph.catIdToIndex(c)])) ? c: cArg;
                        }
                    }
                }

                if (cArg != null) {
                    Map<Integer, Integer> parentCounterMap = new LinkedHashMap();
                    ArrayList<Integer> keys = new ArrayList<Integer>();
                    parentCounterMap.put(cArg, 1);
                    keys.add(cArg);

                    Integer nextParent;

                    for (int i = 0; i < keys.size(); i++) {
                        nextParent = graph.getMaxMinParentPageRank(keys.get(i),T);
                        if (nextParent.equals(keys.get(i)) || (nextParent == -1) || (nextParent == null)) {
                            break;
                        }else if (nextParent >= 0) {
                            if (!parentCounterMap.containsKey(nextParent)) {
                                parentCounterMap.put(nextParent, 1);
                                keys.add(nextParent);
                            } else {
                                parentCounterMap.put(nextParent, parentCounterMap.get(nextParent) + 1);
                                keys.add(nextParent);
                            }
                        }
                    }
                    catMap.put(page, parentCounterMap);
                }
            }
        }
        return catMap;
    }

    /**
     *
     * @param lang
     * @param pageDao
     * @param catDao
     * @return
     *
     * @throws ConfigurationException
     * @throws DaoException
     * @throws FileNotFoundException
     */
    public static LinkedHashMap<LocalPage, Map<Integer, Integer>> traverseAllGraph(Language lang, LocalPageDao pageDao, LocalCategoryMemberDao catDao) throws ConfigurationException, DaoException, FileNotFoundException{
        CategoryGraph graph = catDao.getGraph(lang);
        LinkedHashMap<LocalPage, Map<Integer, Integer>> catMap = new LinkedHashMap();
        Integer max = null;

        for (LocalPage page : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            if (catDao.getCategoryIds(page) != null) {
                ArrayList<Integer> cats = new ArrayList<Integer>(catDao.getCategoryIds(page));
                Map<Integer, Integer> traversalCounterMap = new LinkedHashMap();
                //ArrayList<Integer> keys = new ArrayList<Integer>();

                for (int i = 0; i < cats.size(); i++) {
                    int c = cats.get(i);
                    if (!traversalCounterMap.containsKey(c)) {
                       // traversalCounterMap.put(c, traversalCounterMap.get(c) + 1);
                    //} else {
                        traversalCounterMap.put(c, 1);
                        int[] par = graph.getFamilyMembersCategories(c, "parent");
                        cats.addAll(Ints.asList(par));
                    }
                }
                catMap.put(page, traversalCounterMap);
                max = (max == null|| traversalCounterMap.size() > max) ? traversalCounterMap.size() : max;
            }
        }
        System.out.println(max);
        return catMap;
    }

    /**
     *
     * @param lang
     * @param pageDao
     * @return
     * @throws ConfigurationException
     * @throws DaoException
     * @throws FileNotFoundException
     */
    public static LinkedHashMap<LocalPage, Map<String, Float>> getAnchorText(Env env,Language lang, LocalPageDao pageDao) throws ConfigurationException, DaoException, FileNotFoundException{
        Configurator c = env.getConfigurator();
        PhraseAnalyzer pa = c.get(PhraseAnalyzer.class, "anchortext");
        LinkedHashMap<LocalPage, Map<String, Float>> anchorMap = new LinkedHashMap();

        for (LocalPage page : pageDao.get(DaoFilter.normalPageFilter(lang))) {
                LinkedHashMap<String, Float> description = pa.describe(lang, page, 100);
                anchorMap.put(page, description);
        }
        return anchorMap;
    }

    /**
     *
     * @param map
     * @param labelMethod
     * @param graph
     */
    public static void printMapPages(Map map, String labelMethod, CategoryGraph graph) {

        if (labelMethod.equals("anchor")) {
            LinkedHashMap<LocalPage, Map<String, Float>> anchorMap = new LinkedHashMap<LocalPage, Map<String, Float>>(map);
            for (LocalPage key : anchorMap.keySet()) {
                String allAnchors = "";
                if (anchorMap.get(key) != null) {
                    for (Map.Entry<String, Float> entry : anchorMap.get(key).entrySet()) {
                        allAnchors = allAnchors + "\t" + entry.getKey() + ":" + entry.getValue();
                    }
                } else {
                    allAnchors = "";
                }
                //System.out.println(key.getLocalId() + "\t" + key.getTitle() + allAnchors);
                System.out.println(key.getLocalId() + allAnchors);
            }
        } else if (labelMethod.equals("category")) {
            String catName;
            String finName;
            LinkedHashMap<LocalPage, Map<Integer, Integer>> catMap = new LinkedHashMap<LocalPage, Map<Integer, Integer>>(map);
            for (LocalPage key : catMap.keySet()) {
                String allCats = "";
                if (catMap.get(key) != null) {
                    for (Map.Entry<Integer, Integer> entry : catMap.get(key).entrySet()) {
                        catName = graph.getCategoryName(entry.getKey());
                        finName = (catName != "") ? catName.split(":")[1] : catName;
                        allCats = allCats + "\t" + finName + ":" + entry.getValue();
                    }
                } else {
                    allCats = "";
                }
                //System.out.println(key.getLocalId() + "\t" + key.getTitle() + allcats);
                System.out.println(key.getLocalId() + allCats);
            }
        }else{
            System.out.println("unknown method");
        }

    }
    public static void main(String[] args) throws ConfigurationException, DaoException, FileNotFoundException {

        Options options = new Options();


        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("output")
                        .withDescription("output directory")
                        .create("o"));

        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("LabelCreator", options);
            return;
        }
        Env env = new EnvBuilder(cmd).build();
        Language lang = env.getDefaultLanguage();

        String output = cmd.hasOption("o") ? cmd.getOptionValue("o") : ".";

        ////////////////////////////////
        // Three components needed
        ////////////////////////////////

        // Component for basic information about articles
        LocalPageDao pageDao = env.getComponent(LocalPageDao.class);

        // Provide categories for an article (and vice versa), and generate the graph
        LocalCategoryMemberDao catDao = env.getComponent(LocalCategoryMemberDao.class);

        CategoryGraph graph = catDao.getGraph(lang);


        LinkedHashMap<LocalPage, Map<Integer, Integer>> catMap = traverseAllGraph(lang,pageDao,catDao);

        PrintStream ps =  new PrintStream(new BufferedOutputStream(new FileOutputStream(output + "/categories.tsv")));
        System.setOut(ps);

        printMapPages(catMap,"category", graph);

        ps.close();



    }
}


