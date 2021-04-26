package pl.idedyk.japanese.dictionary.lucene;

public enum LuceneDatabaseSuggesterAndSpellCheckerSource {
	
	DICTIONARY_ENTRY_WEB(LuceneStatic.dictionaryEntry_web_sugestionList, LuceneStatic.dictionaryEntry_web_spellCheckerList),		
	DICTIONARY_ENTRY_ANDROID(LuceneStatic.dictionaryEntry_android_sugestionList, LuceneStatic.dictionaryEntry_android_spellCheckerList),
	
	KANJI_ENTRY_WEB(LuceneStatic.kanjiEntry_web_sugestionList, LuceneStatic.kanjiEntry_web_spellCheckerList),		
	KANJI_ENTRY_ANDROID(LuceneStatic.kanjiEntry_android_sugestionList, LuceneStatic.kanjiEntry_android_spellCheckerList);		
	
	private String suggestionListFieldName;
	private String spellCheckerListFieldName;
	
	LuceneDatabaseSuggesterAndSpellCheckerSource(String suggestionListFieldName, String spellCheckerListFieldName) {
		this.suggestionListFieldName = suggestionListFieldName;
		this.spellCheckerListFieldName = spellCheckerListFieldName;
	}

	public String getSuggestionListFieldName() {
		return suggestionListFieldName;
	}

	public String getSpellCheckerListFieldName() {
		return spellCheckerListFieldName;
	}
	
	public String getSuggesterCacheFileName() {
		return "suggester-" + getSpellCheckerListFieldName() + ".cache";
	}
}
