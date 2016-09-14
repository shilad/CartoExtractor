package info.cartograph;

import org.apache.commons.cli.*;
import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRBuilder;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class Extractor {

    private final Map<String, Integer> id2Index;
    private final Env env;
    private final Language lang;
    private final Iterable<CartographVector> vectorIter;

    public Extractor(Env env, Language lang, Iterable<CartographVector> vectorIter) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.vectorIter = vectorIter;
        this.id2Index = new HashMap<String, Integer>();
    }

    public void writeAll(String dir) throws IOException, DaoException {
        List<CartographVector> vectors = new ArrayList<CartographVector>();
        for (CartographVector cv : vectorIter) {
            if (cv != null) {
                vectors.add(cv);
                id2Index.put(cv.getId(), id2Index.size() + 1);
            }
        }
        writeIds(vectors, dir + "/ids.tsv");
        writeTitles(vectors, dir + "/names.tsv");
        writeVectors(vectors, dir + "/vectors.tsv");
        writePopularity(vectors, dir + "/popularity.tsv");
        writeLinks(vectors, dir + "/links.tsv");
    }


    public void writeTitles(List<CartographVector> vectors, String pathTitles) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathTitles);
        w.write("id\tname\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getName()+ "\n");
        }
        w.close();
    }

    public void writeIds(List<CartographVector> vectors, String pathIds) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathIds);
        w.write("id\texternalId\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getId()+ "\n");
        }
        w.close();
    }

    public void writeVectors(List<CartographVector> vectors, String pathVectors) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathVectors);
        w.write("id\tvector\n");
        for (CartographVector cv : vectors) {
            int index = id2Index.get(cv.getId());
            w.write(index + "");
            for (float x : cv.getVector()) {
               w.write("\t" + Float.toString(x));
            }
            w.write("\n");
        }
        w.close();
    }

    public void writePopularity(List<CartographVector> vectors, String pathPop) throws DaoException, IOException {
        BufferedWriter w = WpIOUtils.openWriter(pathPop);
        w.write("id\tpopularity\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getPopularity()+ "\n");
        }
        w.close();
    }

    public void writeLinks(List<CartographVector> vectors, String pathLinks) throws DaoException, IOException {
        BufferedWriter w = WpIOUtils.openWriter(pathLinks);
        w.write("id\tlinks\n");
        for (CartographVector cv : vectors) {
            int index = id2Index.get(cv.getId());
            w.write(index + "");
            for (String id2 : cv.getLinkIds()) {
                if (id2Index.containsKey(id2)) {
                    w.write("\t" + id2Index.get(id2));
                }
            }
            w.write("\n");
        }
        w.close();

    }

    public static void main(String args[]) throws ConfigurationException, InterruptedException, WikiBrainException, DaoException, IOException {
        Options options = new Options();

        // Specify the Metrics
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("metric")
                        .withDescription("set a local metric")
                        .create("m"));

        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("output")
                        .withDescription("output directory")
                        .create("o"));

        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("vectors")
                        .withDescription("WMF vector file")
                        .create("v"));

        // Specify the minimum number of hours worth of pageviews
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("hours")
                        .withDescription("hours worth of page views")
                        .create("r"));

        EnvBuilder.addStandardOptions(options);


        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SRBuilder", options);
            return;
        }
        Env env = new EnvBuilder(cmd).build();
        Language lang = env.getDefaultLanguage();

        String output = cmd.hasOption("o") ? cmd.getOptionValue("o") : ".";

        // Build word2vec if necessary

        Iterable<CartographVector> iter;
        if (cmd.hasOption("v")) {
            iter = new WMFPageNavVectorizer(env, lang, new File(cmd.getOptionValue("v")));
        } else {
            String metric = cmd.hasOption("m") ? cmd.getOptionValue("m") : "prebuiltword2vec";
            SRBuilder builder = new SRBuilder(env, metric, env.getDefaultLanguage());
            builder.setDeleteExistingData(false);
            builder.setSkipBuiltMetrics(true);
            builder.build();

            // Ensure enough page views are loaded
            PageViewDao pvd = env.getComponent(PageViewDao.class);
            Map<Language, SortedSet<DateTime>> loaded = pvd.getLoadedHours();
            int toLoad = cmd.hasOption("r") ? Integer.valueOf(cmd.getOptionValue("r")) : 5;
            if (loaded.containsKey(lang)) {
                toLoad -= loaded.get(lang).size();
            }
            if (toLoad > 0) {
                pvd.ensureLoaded(selectRandomIntervals(toLoad), new LanguageSet(lang));
            }

            SRMetric sr = env.getComponent(SRMetric.class, metric, lang);
            iter = new SRVectorizer(env, sr);
        }
        Extractor ext = new Extractor(env, lang, iter);
        ext.writeAll(output);

    }


    private static List<Interval> selectRandomIntervals(int n) {
        DateTime now = DateTime.now();
        Interval interval = new Interval(now.plusDays(-465), now.plusDays(-100));
        Hours hours = interval.toDuration().toStandardHours();
        ArrayList result = new ArrayList();
        Random random = new Random();

        for(int i = 0; i < n; ++i) {
            int begOffset = random.nextInt(hours.getHours());
            DateTime start = interval.getStart().plusHours(begOffset);
            DateTime end = start.plusHours(1);
            result.add(new Interval(start, end));
        }

        return result;
    }
}
