package pl.idedyk.japanese.dictionary.lucene;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LetterTokenizer;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.api.dictionary.Utils;

public class LuceneAnalyzer extends Analyzer {

	private final Version matchVersion;
	
	private final boolean removePolishChars;

	public LuceneAnalyzer(Version matchVersion) {
		this(matchVersion, false);
	}

	public LuceneAnalyzer(Version matchVersion, boolean removePolishChars) {
		this.matchVersion = matchVersion;
		
		this.removePolishChars = removePolishChars;
	}
	
	@Override
	protected TokenStreamComponents createComponents(final String fieldName,
			final Reader reader) {
		return new TokenStreamComponents(new Tokenizer(matchVersion, reader));
	}

	private class Tokenizer extends LetterTokenizer {

		public Tokenizer(Version matchVersion, Reader in) {
			super(matchVersion, in);
		}

		public Tokenizer(Version matchVersion, AttributeFactory factory, Reader in) {
			super(matchVersion, factory, in);
		}

		@Override
		protected int normalize(int c) {

			if (removePolishChars == true) {
				
				char[] chars = Character.toChars(c);
				
				boolean containtsPolishChar = false;
				
				for (int idx = 0; idx < chars.length; ++idx) {
					
					char newChar = Utils.removePolishChar(chars[idx]);
					
					if (newChar != chars[idx]) {
						containtsPolishChar = true;
					}
					
					chars[idx] = newChar;
				}
				
				if (containtsPolishChar == true) {
				
					if (chars.length != 1) {
						return Character.toLowerCase(c);
					}
					
					return Character.toLowerCase((int)chars[0]);
					
				} else {
					return Character.toLowerCase(c);
				}
				
			} else {
				return Character.toLowerCase(c);
			}
		}
		
		@Override
		protected boolean isTokenChar(int c) {
			return Character.isLetter(c) || Character.isDigit(c) || (char)c == '・';
		}
	}
}

