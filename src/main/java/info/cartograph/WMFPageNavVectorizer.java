package info.cartograph;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.iterators.TransformIterator;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.NameSpace;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Shilad Sen
 */
public class WMFPageNavVectorizer implements Iterable<CartographVector> {
    private static final Logger LOG = LoggerFactory.getLogger(WMFPageNavVectorizer.class);
    private final Env env;
    private final Language lang;
    private final PagePopularity pop;
    private final LocalPageDao pageDao;
    private final LocalLinkDao linkDao;
    private final File file;
    private int vectorLength = -1;

    public WMFPageNavVectorizer(Env env, Language lang, File file) throws ConfigurationException, DaoException {
        this.env = env;
        this.file = file;
        this.lang = lang;
        this.pageDao = env.getComponent(LocalPageDao.class);
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.pop = new PagePopularity(env, lang);
    }

    public Iterator<CartographVector> iterator() {
        BufferedReader reader;
        try {
            reader = WpIOUtils.openBufferedReader(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final AtomicInteger lineNum = new AtomicInteger();
        return new TransformIterator<String, CartographVector>(
                new LineIterator(reader),
                new Transformer<String, CartographVector>() {
                    public CartographVector transform(String line) {
                        int n = lineNum.getAndIncrement();
                        if (n % 100000 == 0) {
                            LOG.info("Processing line " + n);
                        }
                        // Read header header
                        if (vectorLength < 0) {
                            vectorLength = Integer.valueOf(line.split("\\s+")[1]);
                        } else {
                            try {
                                return makeVector(line);
                            } catch (DaoException e) {
                                LOG.warn("Error when processing line " +
                                        StringEscapeUtils.escapeJavaScript(line), e);
                            }
                        }
                        return null;
                    }
                }
        );
    }

    protected CartographVector makeVector(String line) throws DaoException {
        String tokens[] = line.trim().split(" ");
        if (tokens.length == 0) {
            LOG.warn("Empty line encountered!");
            return null;
        }
        assert(vectorLength > 0);
        if (tokens.length - 1 != vectorLength) {
            LOG.warn("Invalid vector length for {}. Expected {}, found {}.",
                    tokens[0], vectorLength, tokens.length - 1);
            return null;
        }

        String title = tokens[0];
        float v[] = new float[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            v[i] = Float.valueOf(tokens[i+1]);
        }
        int id = pageDao.getIdByTitle(title, lang, NameSpace.ARTICLE);
        if (id <= 0) {
            return null;
        }
        double pp = pop.getPopularity(id);

        List<String> links = new ArrayList<String>();
        for (LocalLink ll : linkDao.getLinks(lang, id, true)) {
            links.add("" + ll.getLocalId());
        }

        return new CartographVector(
                title,
                "" + id,
                links.toArray(new String[links.size()]),
                v,
                pp);
    }
}
