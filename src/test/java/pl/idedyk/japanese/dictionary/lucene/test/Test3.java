package pl.idedyk.japanese.dictionary.lucene.test;

import java.io.File;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.google.gson.Gson;

import pl.idedyk.japanese.dictionary.lucene.LuceneStatic;
import pl.idedyk.japanese.dictionary2.jmdict.xsd.JMdict;

public class Test3 {
	public static void main(String[] args) throws Exception {
		
		Directory index = FSDirectory.open(new File("/tmp/a/db-lucene"));
		//analyzer = new LuceneAnalyzer(Version.LUCENE_47);
		// LuceneAnalyzer analyzerWithoutPolishChars = new LuceneAnalyzer(Version.LUCENE_47, true);
		DirectoryReader reader = DirectoryReader.open(index);
		IndexSearcher searcher = new IndexSearcher(reader);
		
		//
		
		BooleanQuery query = new BooleanQuery();
		
		PhraseQuery objectTypeQuery = new PhraseQuery();
		objectTypeQuery.add(new Term(LuceneStatic.objectType, LuceneStatic.dictionaryEntry2_objectType));

		//
		
		PhraseQuery translatePhraseQuery = new PhraseQuery();

//		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList, "kot"));
//		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList, "tygrysi"));
		
		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList,  "nalozony"));
		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList,  "obraz"));
		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList,  "w"));
		translatePhraseQuery.add(new Term(LuceneStatic.dictionaryEntry2_translatesList,  "rogu"));
		
		//
		
		query.add(objectTypeQuery, Occur.MUST);
		query.add(translatePhraseQuery, Occur.MUST);
		
		//
		
		Gson gson = new Gson();
		
		ScoreDoc[] scoreDocs = searcher.search(query, null, 100).scoreDocs;
		
		for (ScoreDoc scoreDoc : scoreDocs) {			
			Document foundDocument = searcher.doc(scoreDoc.doc);
			
			String entryBody = foundDocument.get(LuceneStatic.dictionaryEntry2_entry);			
			JMdict.Entry entry = gson.fromJson(entryBody, JMdict.Entry.class);
			
			System.out.println(scoreDoc.doc + " - " + scoreDoc.score + " - " + entry.getEntryId());
		}
		
		
		reader.close();
		index.close();
	}
}
