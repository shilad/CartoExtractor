package info.cartograph;

import org.apache.commons.cli.*;
import org.apache.commons.collections15.map.HashedMap;
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
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRBuilder;
import org.wikibrain.sr.SRMetric;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class PageRank {

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
        LocalLinkDao linkDao = env.getComponent(LocalLinkDao.class);
        LocalPageDao pageDao = env.getComponent(LocalPageDao.class);

        final Map<String, Double> ranks = new HashedMap<String, Double>();
        List<String> titles = new ArrayList<String>();
        for (LocalPage p : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            double pr = linkDao.getPageRank(p.toLocalId());
            ranks.put(p.getTitle().getCanonicalTitle(), pr);
            titles.add(p.getTitle().getCanonicalTitle());
        }

        Collections.sort(titles, new Comparator<String>() {
            public int compare(String t1, String t2) {
                return -1 * ranks.get(t1).compareTo(ranks.get(t2));
            }
        });

        for (int i = 0; i < 100; i++) {
            System.out.println(titles.get(i));
        }
    }
}
