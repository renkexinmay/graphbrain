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


import io
from gb.hypergraph.hypergraph import HyperGraph
from gb.reader.reader import Reader


if __name__ == '__main__':
    filename = 'nuclear.txt'
    hgr = HyperGraph({'backend': 'leveldb', 'hg': 'nuclear.hg'})

    reader = Reader(hgr, lang='fr', model_file='hypergen_random_forest.model')

    n = 0
    with io.open(filename, 'r', encoding='utf-8') as f:
        for line in f:
            n += 1
            print('LINE #%s' % n)
            parses = reader.read_text(line, None, reset_context=False)
            for p in parses:
                print('\n')
                print('sentence: %s' % str(p[1].main_edge))
