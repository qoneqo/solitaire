"""
Solitaire Logbook Verifier
==========================
This script verifies that all games in 'logbook.json' can be solved
by the current bot logic.

Usage: python logbook_verifier.py
"""

import json
from copy import deepcopy

SUITS = ['c', 'd', 'h', 's']
SUIT_COLORS = {'c': 'B', 'd': 'R', 'h': 'R', 's': 'B'}

def color(suit): return SUIT_COLORS[suit]

def can_foundation(card, pile):
    if not pile: return card['rank'] == 1
    top = pile[-1]
    return card['suit'] == top['suit'] and card['rank'] == top['rank'] + 1

def can_tableau(card, pile):
    if not pile: return card['rank'] == 13
    top = pile[-1]
    if not top['up']: return False
    return color(card['suit']) != color(top['suit']) and card['rank'] == top['rank'] - 1

def simulate_solve(deck_strings, max_moves=1000):
    deck = [{'suit': s[0], 'rank': int(s[1:]), 'up': False} for s in deck_strings]
    tableaus = [[] for _ in range(7)]
    idx = 0
    for i in range(7):
        for j in range(i + 1):
            c = dict(deck[idx]); c['up'] = (j == i)
            tableaus[i].append(c); idx += 1
    stock_part = deck[idx:]
    stock = list(reversed([dict(c) for c in stock_part])) 
    state = {'tableaus': tableaus, 'stock': stock, 'waste': [], 'foundations': [[], [], [], []]}
    
    def get_move(st):
        if st['waste']:
            for i in range(4):
                if can_foundation(st['waste'][-1], st['foundations'][i]): return ('TF', 'W', i)
        for i in range(7):
            if st['tableaus'][i] and st['tableaus'][i][-1]['up']:
                for j in range(4):
                    if can_foundation(st['tableaus'][i][-1], st['foundations'][j]): return ('TF', 'T', i, j)
        for j in range(7):
            pile = st['tableaus'][j]
            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
            if first_up <= 0: continue
            for i in range(7):
                if i != j and can_tableau(pile[first_up], st['tableaus'][i]): return ('TT', 'T', j, i, len(pile) - first_up)
        if st['waste']:
            for i in range(7):
                if can_tableau(st['waste'][-1], st['tableaus'][i]): return ('TT', 'W', -1, i, 1)
        if st['stock']: return ('DS', None)
        for j in range(7):
            pile = st['tableaus'][j]
            if not pile: continue
            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
            if first_up == -1: continue
            for i in range(7):
                if i != j and st['tableaus'][i] and can_tableau(pile[first_up], st['tableaus'][i]): return ('TT', 'T', j, i, len(pile) - first_up)
        has_hidden = any(any(not c['up'] for c in p) for p in st['tableaus'])
        if has_hidden:
            for i in range(7):
                if not st['tableaus'][i]:
                    if st['waste'] and st['waste'][-1]['rank'] == 13: return ('TT', 'W', -1, i, 1)
                    best_f, best_h = -1, 0
                    for j in range(7):
                        pile = st['tableaus'][j]
                        if pile and pile[0]['up'] == False:
                            first_up = next((k for k, c in enumerate(pile) if c['up']), -1)
                            if first_up > 0 and pile[first_up]['rank'] == 13 and first_up > best_h: best_h, best_f = first_up, j
                    if best_f != -1: return ('TT', 'T', best_f, i, len(st['tableaus'][best_f]) - next(k for k, c in enumerate(st['tableaus'][best_f]) if c['up']))
        if st['waste'] and not st['stock']: return ('RW', None)
        return None

    recycle_count = 0
    for _ in range(max_moves):
        if all(len(f) == 13 for f in state['foundations']): return True
        move = get_move(state)
        if not move: return False
        if move[0] == 'DS':
            c = state['stock'].pop(); c['up'] = True; state['waste'].append(c)
        elif move[0] == 'RW':
            recycle_count += 1
            if recycle_count >= 3: return False
            state['stock'] = list(reversed(state['waste'])); state['waste'] = []
            for c in state['stock']: c['up'] = False
        elif move[0] == 'TF':
            recycle_count = 0
            if move[1] == 'W': state['foundations'][move[2]].append(state['waste'].pop())
            else:
                fi, ti = move[2], move[3]
                state['foundations'][ti].append(state['tableaus'][fi].pop())
                if state['tableaus'][fi] and not state['tableaus'][fi][-1]['up']: state['tableaus'][fi][-1]['up'] = True
        elif move[0] == 'TT':
            if move[1] == 'W': state['tableaus'][move[3]].append(state['waste'].pop())
            else:
                fi, ti, cnt = move[2], move[3], move[4]
                stack = state['tableaus'][fi][-cnt:]
                state['tableaus'][fi] = state['tableaus'][fi][:-cnt]
                if state['tableaus'][fi] and not state['tableaus'][fi][-1]['up']: state['tableaus'][fi][-1]['up'] = True
                state['tableaus'][ti].extend(stack)
    return False

def main():
    logbook_path = 'app/src/main/assets/solitaire/logbook.json'
    with open(logbook_path, 'r') as f:
        logbook = json.load(f)
    print(f"Verifying {len(logbook)} games...")
    for entry in logbook:
        if not simulate_solve(entry['deck']):
            print(f"  Game {entry['id']} is UNWINNABLE!")
            return
    print("All games are winnable! SUCCESS")

if __name__ == '__main__':
    main()
