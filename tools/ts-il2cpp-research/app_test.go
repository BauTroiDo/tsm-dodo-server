package main

import (
	"path/filepath"
	"testing"
)

var (
	testLib  = filepath.Join("..", "..", "docs", "research", "il2cpp", "TS-Origin-VTC-1.3", "native", "arm64-v8a", "libil2cpp.so")
	testMeta = filepath.Join("..", "..", "docs", "research", "il2cpp", "TS-Origin-VTC-1.3", "metadata", "global-metadata.dat")
)

func TestRealDVTPair(t *testing.T) {
	app := NewApp()
	info, err := app.InspectPair(testLib, testMeta)
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("ELF=%s/%s metadata=%s v%d", info.ELFClass, info.ELFMachine, info.MetadataSanity, info.MetadataVersion)
	if info.MetadataSanity != "0xFAB11BAF" {
		t.Fatalf("unexpected metadata sanity: %s", info.MetadataSanity)
	}
}

func TestRealDVTSearch(t *testing.T) {
	app := NewApp()
	hits, err := app.SearchPair(testLib, testMeta, "Compound,aDegree,bDegree,cDegree,14005,14011,14019", "auto")
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("hits=%d", len(hits))
	counts := map[string]int{}
	for _, h := range hits {
		counts[h.Query]++
	}
	for q, n := range counts {
		t.Logf("%s=%d", q, n)
	}
	if len(hits) == 0 {
		t.Fatal("no hits")
	}
}

func TestDumpStringsRealDVT(t *testing.T) {
	app := NewApp()
	out, err := app.DumpStrings(testLib, testMeta, t.TempDir())
	if err != nil {
		t.Fatal(err)
	}
	if len(out) != 2 {
		t.Fatalf("expected 2 dumps, got %d", len(out))
	}
	for _, p := range out {
		t.Log(p)
	}
}
