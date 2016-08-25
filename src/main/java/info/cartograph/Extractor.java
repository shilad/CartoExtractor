package info.cartograph;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
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
import org.wikibrain.core.dao.DaoFilter;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRBuilder;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.vector.DenseVectorGenerator;
import org.wikibrain.sr.vector.DenseVectorSRMetric;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class Extractor {
    private final Env env;
    private final DenseVectorSRMetric sr;
    private final PageViewDao viewDao;
    private final Language lang;
    private final LocalPageDao pageDao;
    private final TIntIntMap id2Index;
    private final LocalLinkDao linkDao;

    public Extractor(Env env, Language lang, DenseVectorSRMetric metric) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.sr = metric;
        this.viewDao = env.getComponent(PageViewDao.class);
        this.pageDao = env.getComponent(LocalPageDao.class);
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.id2Index = makeIndexes();
    }

    public void writeAll(String dir) throws IOException, DaoException {
        writeIds(dir + "/ids.tsv");
        writeTitles(dir + "/names.tsv");
        writeVectors(dir + "/vectors.tsv");
        writePopularity(dir + "/popularity.tsv");
        writeLinks(dir + "/links.tsv");
    }


    public void writeTitles(String pathTitles) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathTitles);
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            int index = id2Index.get(p.getLocalId());
            assert(index != id2Index.getNoEntryValue());
            w.write(index + "\t" + p.getTitle().getCanonicalTitle() + "\n");
        }
        w.close();
    }

    public void writeIds(String pathIds) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathIds);
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            int index = id2Index.get(p.getLocalId());
            assert(index != id2Index.getNoEntryValue());
            w.write(index + "\t" + p.getLocalId() + "\n");
        }
        w.close();
    }

    public void writeVectors(String pathVectors) throws IOException, DaoException {
        DenseVectorGenerator gen = sr.getGenerator();
        BufferedWriter w = WpIOUtils.openWriter(pathVectors);
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            float[] v = gen.getVector(p.getLocalId());
            if (v == null) continue;
            int index = id2Index.get(p.getLocalId());
            assert(index != id2Index.getNoEntryValue());
            w.write(index + "");
            for (float x : v) {
               w.write("\t" + Float.toString(x));
            }
            w.write("\n");
        }
        w.close();
    }

    public void writePopularity(String pathPop) throws DaoException, IOException {
        TIntIntMap pageViews = getMedianViews();
        DenseVectorGenerator gen = sr.getGenerator();
        BufferedWriter w = WpIOUtils.openWriter(pathPop);
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            int pv = pageViews.containsKey(p.getLocalId()) ? pageViews.get(p.getLocalId()) : 1;
            double pr = linkDao.getPageRank(lang, p.getLocalId());
            double pop = pv * pr;
            int index = id2Index.get(p.getLocalId());
            assert(index != id2Index.getNoEntryValue());
            w.write(index + "\t" + pop + "\n");
        }
        w.close();
    }

    public void writeLinks(String pathLinks) throws DaoException, IOException {
        DenseVectorGenerator gen = sr.getGenerator();
        BufferedWriter w = WpIOUtils.openWriter(pathLinks);
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            int index = id2Index.get(p.getLocalId());
            assert(index != id2Index.getNoEntryValue());
            w.write(index + "");
            for (LocalLink ll : linkDao.getLinks(lang, p.getLocalId(), true)) {
                int index2 = id2Index.get(ll.getDestId());
                if (index2 != id2Index.getNoEntryValue()) {
                    w.write("\t" + index2);
                }
            }
            w.write("\n");
        }
        w.close();

    }

    private TIntIntMap makeIndexes() throws DaoException {
        TIntIntMap map = new TIntIntHashMap();
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            map.put(p.getLocalId(), map.size() + 1);
        }
        return map;
    }

    private TIntIntMap getMedianViews() throws DaoException {
        Map<Language, SortedSet<DateTime>> hours = viewDao.getLoadedHours();
        Map<Integer, TIntList> pageSamples = new HashMap<Integer, TIntList>();
        for (DateTime dt : hours.get(lang)) {
            TIntIntMap pv = viewDao.getAllViews(lang, dt.minusMinutes(1), dt.plusMinutes(1));
            for (int pageId : pv.keys()) {
                if (!pageSamples.containsKey(pageId)) {
                    pageSamples.put(pageId, new TIntArrayList());
                }
                pageSamples.get(pageId).add(pv.get(pageId));
            }
        }
        TIntIntMap medians = new TIntIntHashMap();
        for (int pageId : pageSamples.keySet()) {
            TIntList sample = pageSamples.get(pageId);
            sample.sort();
            medians.put(pageId, sample.get(sample.size() / 2));
        }
        return medians;
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

        // Specify the minimum number of hours worth of pageviews
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("hours")
                        .withDescription("hours worth of page views")
                        .create("1"));

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

        String metric = cmd.hasOption("m") ? cmd.getOptionValue("m") : "prebuiltword2vec";
        String output = cmd.hasOption("o") ? cmd.getOptionValue("o") : ".";

        // Build word2vec if necessary
        SRBuilder builder = new SRBuilder(env, metric, env.getDefaultLanguage());
        builder.setDeleteExistingData(false);
        builder.setSkipBuiltMetrics(true);
        builder.build();

        // Ensure enough page views are loaded
        PageViewDao pvd = env.getComponent(PageViewDao.class);
        Map<Language, SortedSet<DateTime>> loaded = pvd.getLoadedHours();
        int toLoad = cmd.hasOption("h") ? Integer.valueOf(cmd.getOptionValue("h")) : 5;
        if (loaded.containsKey(lang)) {
            toLoad -= loaded.get(lang).size();
        }
        if (toLoad > 0) {
            pvd.ensureLoaded(selectRandomIntervals(toLoad), new LanguageSet(lang));
        }

        Extractor ext = new Extractor(
                env, lang,
                (DenseVectorSRMetric) env.getComponent(SRMetric.class, metric, lang));
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
