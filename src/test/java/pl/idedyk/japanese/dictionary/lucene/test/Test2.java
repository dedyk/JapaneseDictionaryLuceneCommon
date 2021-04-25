package pl.idedyk.japanese.dictionary.lucene.test;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.util.Version;

import pl.idedyk.japanese.dictionary.lucene.LuceneAnalyzer;

public class Test2 {

	public static void main(String[] args) throws Exception {
		
		LuceneAnalyzer analyzerWithoutPolishChars = new LuceneAnalyzer(Version.LUCENE_47, true);
		
		/*
		ZAPIS
		
		Directory index = FSDirectory.open(new File("/tmp/a/db-lucene"));
		
		IndexReader reader = DirectoryReader.open(index);
		
		LuceneDictionary luceneDictionary = new LuceneDictionary(reader, LuceneDatabaseSuggesterAndSpellCheckerSource.KANJI_ENTRY_ANDROID.getSuggestionListFieldName());		
		Lookup lookup = new AnalyzingSuggester(analyzerWithoutPolishChars);
		
		lookup.build(luceneDictionary);
		
		//
				
		lookup.store(new FileOutputStream(new File("/tmp/a/AAAA")));
		*/
		
		// ODCZYT
		Lookup lookup = new AnalyzingSuggester(analyzerWithoutPolishChars);
		
		lookup.load(new FileInputStream(new File("/tmp/a/AAAA")));
		
		List<LookupResult> lookupResultList = lookup.lookup("kot", false, 30);
		
		for (LookupResult lookupResult : lookupResultList) {
			System.out.println(lookupResult.key);
		}
	}

}
