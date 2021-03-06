import progressbar
from graphbrain.funs import *
import graphbrain.constants as cons
import graphbrain.nlp.parser as par
from graphbrain.synonyms.meronomy import Meronomy


def generate(hg):
    print('starting parser...')
    parser = par.Parser()

    mer = Meronomy(hg, parser)

    print('reading edges...')
    total_edges = 0
    total_beliefs = 0

    total_verts = hg.symbol_count() + hg.edge_count()
    i = 0
    with progressbar.ProgressBar(max_value=total_verts) as bar:
        for vertex in hg.all():
            if is_edge(vertex):
                edge = vertex
                total_edges += 1
                if hg.is_belief(edge):
                    mer.add_edge(edge)
                    total_beliefs += 1
            i += 1
            if (i % 1000) == 0:
                bar.update(i)

    print('edges: %s; beliefs: %s' % (total_edges, total_beliefs))

    print('post assignments...')
    i = 0
    with progressbar.ProgressBar(max_value=total_verts) as bar:
        for vertex in hg.all():
            if is_edge(vertex):
                edge = vertex
                if hg.is_belief(edge):
                    mer.post_assignments(edge)
            i += 1
            if (i % 1000) == 0:
                bar.update(i)

    print('generating meronomy graph...')
    mer.generate()

    print('normalizing meronomy graph...')
    mer.normalize_graph()

    print('generating synonyms...')
    mer.generate_synonyms()

    print('writing synonyms...')
    i = 0
    with progressbar.ProgressBar(max_value=len(mer.synonym_sets)) as bar:
        for syn_id in mer.synonym_sets:
            edges = set()
            for atom in mer.synonym_sets[syn_id]:
                if atom in mer.edge_map:
                    edges |= mer.edge_map[atom]
            best_count = -1
            best_label_edge = None
            for edge in edges:
                if mer.edge_counts[edge] > best_count:
                    best_count = mer.edge_counts[edge]
                    best_label_edge = edge
            label = hg.get_label(best_label_edge)
            syn_symbol = build_symbol(label, 'syn%s' % syn_id)
            for edge in edges:
                syn_edge = (cons.are_synonyms, edge, syn_symbol)
                hg.add(syn_edge)
            label_symbol = build_symbol(label, cons.label_namespace)
            label_edge = (cons.has_label, syn_symbol, label_symbol)
            hg.add(label_edge)
            i += 1
            if i % 1000 == 0:
                bar.update(i)
        bar.update(i)

    print('%s synonym sets created' % len(mer.synonym_sets))
    print('done.')


def synonyms(hg, edge):
    edges1 = hg.pattern2edges([cons.are_synonyms, None, edge])
    edges2 = hg.pattern2edges([cons.are_synonyms, edge, None])
    return set([e[1] for e in edges1]).union(set([e[2] for e in edges2])).union({edge})


def synonyms_degree(hg, edge):
    syns = synonyms(hg, edge)
    degs = [hg.degree(syn) for syn in syns]
    return sum(degs)


def main_synonym(hg, edge, in_adp=False):
    """Finds the main synonym of an edge or symbol. The main synonym is usually a special type
       of symbol that all synonyms point to, used as an identifier for the synonym set.

       If parameter in_adp is True, in case of adpositional phrases this function looks for the main
       synonym contained in the phrase. E.g. in (+/gb with/nlp.with.adp india/nlp.india.propn)
       the main synonym for india/nlp.india.propn is returned.

       In case no main synonym exists, the edge or symbol itself is returned."""
    if in_adp and is_edge(edge):
        if len(edge) == 3 and edge[0] == '+/gb':
            if not is_edge(edge[1]) and edge[1][-4:] == '.adp':
                return main_synonym(hg, edge[2])
            elif not is_edge(edge[2]) and edge[2][-4:] == '.adp':
                return main_synonym(hg, edge[1])
    edges = hg.pattern2edges([cons.are_synonyms, edge, None])
    if len(edges) > 0:
        return edges.pop()[2]
    return edge
