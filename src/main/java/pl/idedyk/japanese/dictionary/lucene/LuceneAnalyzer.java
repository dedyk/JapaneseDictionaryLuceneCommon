package pl.idedyk.japanese.dictionary.lucene;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.util.Version;

public class LuceneAnalyzer extends Analyzer {

	private final Version matchVersion;

	public LuceneAnalyzer(Version matchVersion) {
		this.matchVersion = matchVersion;
	}

	@Override
	protected TokenStreamComponents createComponents(final String fieldName,
			final Reader reader) {
		return new TokenStreamComponents(new Tokenizer(matchVersion, reader));
	}

	private static class Tokenizer extends LetterTokenizer {

		public Tokenizer(Version matchVersion, Reader in) {
			super(matchVersion, in);
		}

		public Tokenizer(Version matchVersion, AttributeFactory factory, Reader in) {
			super(matchVersion, factory, in);
		}

		@Override
		protected int normalize(int c) {
			return Character.toLowerCase(c);
		}
		
		@Override
		protected boolean isTokenChar(int c) {
			return	Character.isLetter(c) ||
					Character.isDigit(c);
		}
	}
}

