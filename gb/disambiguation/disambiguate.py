#   Copyright (c) 2016 CNRS - Centre national de la recherche scientifique.
#   All rights reserved.
#
#   Written by Telmo Menezes <telmo@telmomenezes.com>
#
#   This file is part of GraphBrain.
#
#   GraphBrain is free software: you can redistribute it and/or modify
#   it under the terms of the GNU Affero General Public License as published by
#   the Free Software Foundation, either version 3 of the License, or
#   (at your option) any later version.
#
#   GraphBrain is distributed in the hope that it will be useful,
#   but WITHOUT ANY WARRANTY; without even the implied warranty of
#   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#   GNU Affero General Public License for more details.
#
#   You should have received a copy of the GNU Affero General Public License
#   along with GraphBrain.  If not, see <http://www.gnu.org/licenses/>.


import gb.hypergraph.hypergraph as hyperg
import gb.hypergraph.symbol as sym
import gb.hypergraph.edge as ed
import gb.knowledge.synonyms as syns
import gb.knowledge.neighborhoods as knei
from gb.disambiguation.candidate_metrics import CandidateMetrics


def is_part_of(root, main_root):
    if root == main_root:
        return True
    root_parts = main_root.split('_')
    for part in root_parts:
        if root == part:
            return True
    return False


def connected_symbols_with_root(hg, symbol, root):
    symbols = {}
    edges = hg.edges_with_symbols((symbol,), root=root)
    for edge in edges:
        symbs = ed.symbols(edge)
        for symb in symbs:
            if sym.root(symb) == root:
                if symb not in symbols:
                    symbols[symb] = hg.degree(symb)
    return symbols


def probability_of_meaning(hg, symbol, bag_of_words, exclude):
    total_degree = hg.total_degree()
    symbol_root = sym.root(symbol)
    prob = 1.
    for ngram in bag_of_words:
        ngram_symbol = sym.str2symbol(ngram)
        if not ((ngram in exclude) or is_part_of(ngram_symbol, symbol_root)):
            neighbors = connected_symbols_with_root(hg, symbol, ngram_symbol)
            for neighb in neighbors:
                prob *= float(neighbors[neighb]) / float(total_degree)
    return prob


def word_overlap(hg, symbol, bag_of_words, exclude):
    symbols = knei.ego(hg, symbol)
    roots = [sym.root(s) for s in symbols]
    words = set()
    for root in roots:
        ngram = sym.symbol2str(root)
        if ngram not in exclude:
            for word in root.split('_'):
                words.add(word)
    return len(bag_of_words.intersection(words))


def candidate_metrics(hg, symbol, bag_of_words, exclude):
    cm = CandidateMetrics()
    cm.prob_meaning = probability_of_meaning(hg, symbol, bag_of_words, exclude)
    cm.degree = syns.degree(hg, symbol)
    if cm.degree < 1000:
        cm.word_overlap = word_overlap(hg, symbol, bag_of_words, exclude)
    return cm


def disambiguate(hg, roots, bag_of_words, exclude):
    # print('*** %s' % str(bag_of_words))
    candidates = set()
    for root in roots:
        candidates = candidates.union(hg.symbols_with_root(root))
    best = None
    best_cm = CandidateMetrics()
    for candidate in candidates:
        cm = candidate_metrics(hg, candidate, bag_of_words, exclude)
        # print('%s %s' % (candidate, cm))
        if cm.better_than(best_cm):
            best_cm = cm
            best = candidate

    return best, best_cm


if __name__ == '__main__':
    hgr = hyperg.HyperGraph({'backend': 'leveldb',
                             'hg': 'wikidata.hg'})
    bag_of_words1 = {'berlin', 'city'}
    print(disambiguate(hgr, 'berlin', bag_of_words1, ()))
    bag_of_words2 = {'berlin', 'car'}
    print(disambiguate(hgr, 'berlin', bag_of_words2, ()))