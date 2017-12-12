package info.cartograph;

import com.google.common.primitives.Ints;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.collections15.map.HashedMap;
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
import org.wikibrain.utils.WpIOUtils;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by Shilad Sen
 */
public class CategoryLabelCreator {
    public static final Logger LOG = Logger.getLogger(CategoryLabelCreator.class.getName());

    public static void writePages(Language lang, LocalPageDao pageDao, CategoryGraph graph, Map<Integer, int[]> catAncestors, String pathOut) throws DaoException, IOException {
        LOG.info("counting categories");

        // Count the number of categories per page
        TIntIntMap pageCatCounts = new TIntIntHashMap();
        for (int[] catPages : graph.catPages) {
            for (int pageId : catPages) {
                pageCatCounts.adjustOrPutValue(pageId, 1, 1);
            }
        }

        // Allocate and fill a datastructure from a page to its categories
        LOG.info("creating page -> category mapping");
        Map<Integer, int[]> pageCats = new HashMap<Integer, int[]>();
        for (int pageId : pageCatCounts.keys()) {
            pageCats.put(pageId, new int[pageCatCounts.get(pageId)]);
        }
        for (int catIndex = 0; catIndex < graph.catPages.length; catIndex++) {
            for (int pageId : graph.catPages[catIndex]) {
                int i =  pageCatCounts.adjustOrPutValue(pageId, -1, -1);
                pageCats.get(pageId)[i] = catIndex;
            }
        }
        for (int i : pageCatCounts.values()) assert(i == 0);

        // Output the pages.
        LOG.info("writing category labels to " + pathOut);
        BufferedWriter writer = WpIOUtils.openWriter(pathOut);
        for (LocalPage page : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            writer.write(page.getLocalId());
            if (!pageCats.containsKey(page.getLocalId())) continue;
            TIntSet cats = new TIntHashSet();
            for (int catIndex : pageCats.get(page.getLocalId())) {
                cats.add(catIndex);
                cats.addAll(catAncestors.get(catIndex));
            }

            for (int catIndex : cats.toArray()) {
                String parts[] = graph.cats[catIndex].split(":", 2);
                writer.write("\t" + parts[parts.length - 1]);
            }
            writer.write("\n");
        }
        writer.close();
    }

    public static  Map<Integer, int[]> buildParentCategories(CategoryGraph graph) {
        Map<Integer, int[]> result = new HashedMap<Integer, int[]>();
        for (int i = 0; i < graph.catParents.length; i++) {
            TIntSet ancestors = new TIntHashSet();
            getAncestors(i, graph, ancestors);
            result.put(i, ancestors.toArray());
        }
        return result;
    }

    public static void getAncestors(int catIndex, CategoryGraph graph, TIntSet ancestors) {
        if (ancestors.contains(catIndex)) {
            return;
        }
        ancestors.add(catIndex);
        for (int parentIndex : graph.catParents[catIndex]) {
            if (graph.catCosts[parentIndex] >= graph.catCosts[catIndex] && !ancestors.contains(parentIndex)) {
                getAncestors(parentIndex, graph, ancestors);
            }
        }
    }

    public static void main(String[] args) throws ConfigurationException, DaoException, IOException {

        Options options = new Options();


        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("output")
                        .withDescription("output directory")
                        .create("o"));

        EnvBuilder.addStandardOptions(options);

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

        // Get the graph
        CategoryGraph graph = catDao.getGraph(lang);

        Map<Integer, int[]> ancestors = buildParentCategories(graph);

        writePages(lang, pageDao, graph, ancestors, output + "/categories.tsv");

    }
}


