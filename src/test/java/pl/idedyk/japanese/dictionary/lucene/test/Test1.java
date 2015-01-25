package pl.idedyk.japanese.dictionary.lucene.test;

import java.util.ArrayList;
import java.util.List;

import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordRequest;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordResult;
import pl.idedyk.japanese.dictionary.api.dictionary.dto.FindWordResult.ResultItem;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntry;
import pl.idedyk.japanese.dictionary.api.dto.DictionaryEntryType;
import pl.idedyk.japanese.dictionary.lucene.LuceneDatabase;

public class Test1 {

	public static void main(String[] args) throws Exception {
		
		LuceneDatabase luceneDatabase = new LuceneDatabase("db-lucene");
		
		luceneDatabase.open();

		FindWordRequest findWordRequest = new FindWordRequest();
		
		findWordRequest.searchGrammaFormAndExamples = false;
		
		findWordRequest.word = "shukujitsu";

		DictionaryEntryType[] dictionaryEntryTypeValues = DictionaryEntryType.values();
		
		List<DictionaryEntryType> dictionaryEntryTypeList = new ArrayList<DictionaryEntryType>();
		
		for (DictionaryEntryType dictionaryEntryType : dictionaryEntryTypeValues) {
			dictionaryEntryTypeList.add(dictionaryEntryType);
		}
		
		//dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_NOUN);
		dictionaryEntryTypeList.remove(DictionaryEntryType.WORD_ADJECTIVE_NO);
		
		findWordRequest.dictionaryEntryTypeList = dictionaryEntryTypeList;
		
		FindWordResult findWordResult = luceneDatabase.findDictionaryEntries(findWordRequest);
		
		for (ResultItem resultItem : findWordResult.result) {
			
			DictionaryEntry dictionaryEntry = resultItem.getDictionaryEntry();
			
			System.out.println(dictionaryEntry.getId() + " - " + dictionaryEntry.getDictionaryEntryTypeList() + " - " + dictionaryEntry.getKanji() + " - " + dictionaryEntry.getKana() + " - " + dictionaryEntry.getTranslates());
			
			System.out.println("--------");
		}
		
		
		luceneDatabase.close();
	}
}
