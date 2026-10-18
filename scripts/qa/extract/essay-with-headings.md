# Game Theory

## 1. Introduction

### 1.1 What a game is

In game theory a **game** is any situation in which the outcome for each participant depends on the choices of the
others as well as on their own. The participants are called **players**, the choices open to them **strategies**,
and what each player gets from each combination of strategies is their **payoff**. A game can be as small as two
children deciding who gets the larger slice of cake, or as large as the governments of the world negotiating over
fishing quotas.

When I was nine my uncle taught me to play noughts and crosses against him on the back of a restaurant menu. He won
the first eleven games. By the twelfth I had noticed that he always started in a corner, and I started answering in
the centre; we drew every game after that, and he soon lost interest. Neither of us knew it, but we had found the
game's solution between us.

### 1.2 Simultaneous and sequential games

In a **simultaneous game** players choose without knowing what the others chose, as in rock-paper-scissors or a
sealed-bid auction. In a **sequential game** they move in turn and each sees the earlier moves, as in chess.
Simultaneous games are usually written as a **payoff matrix**, sequential games as a **game tree**.

### 1.3 Zero-sum games

A game is **zero-sum** when one player's gain is exactly another player's loss, so the payoffs in every outcome add up
to zero. Poker among friends is zero-sum: the money only changes hands. Most real interactions are not zero-sum.
Trade, for example, can leave both sides better off, which is why people trade at all.

### 1.4 Dominant strategies

A strategy is **dominant** if it gives a player a better payoff than any other strategy, whatever the other players
do. A rational player with a dominant strategy plays it. When every player has one, the game's outcome is easy to
predict, though it is not always good for the players, as the next section shows.

### 1.5 Nash equilibrium

The central idea of non-cooperative game theory is the **Nash equilibrium**, named after John Nash, who defined it in
his doctoral thesis at Princeton in 1950. A Nash equilibrium is a combination of strategies, one per player, such that
**no player can do better by changing their own strategy while the others keep theirs**. Each player's strategy is a
**best response** to the strategies of the others.

The idea is easiest to see in a small example. Two cafés on the same street each choose a price, high or low. If both
charge high prices they share the customers and each makes a good profit. If one charges low and the other high, the
cheap one takes most of the customers. If both charge low, they share the customers again but each makes little. For
each café, charging low is the best response to either price the other might charge, so the only Nash equilibrium is
for both to charge low, even though both would prefer the outcome where both charge high.

This shape of game, where each player's individually rational choice leads to an outcome that is worse for everyone,
is called the **prisoner's dilemma**. The name comes from a story Albert Tucker told to an audience of psychologists at
Stanford in 1950. Two suspects are held in separate cells. Each is offered the same deal: betray the other and go free
while the other serves a long sentence, unless the other also betrays, in which case both serve a medium sentence; if
both stay silent, both serve a short sentence. Betraying is a dominant strategy for each, so both betray and both serve
the medium sentence. Tucker is said to have invented the story on the spot because the psychologists found the payoff
matrix too abstract.

A Nash equilibrium need not be unique. In a **coordination game** two or more equilibria exist and the players only
need to choose the same one. Which side of the road to drive on is the standard example: driving on the left is an
equilibrium if everyone else does, and so is driving on the right. Sweden switched from the left to the right on a
single morning in September 1967, a day that became known as Dagen H. Traffic stopped across the country at ten to five
in the morning, every car moved carefully across to the other side of the road, and traffic started again at five.
Accidents fell in the months that followed, probably because drivers were unusually careful.

Nor does every game have an equilibrium in **pure strategies**, where each player picks one strategy for certain. In
rock-paper-scissors any fixed choice can be beaten. Nash's theorem says that every game with finitely many players and
strategies has at least one equilibrium once **mixed strategies** are allowed, in which a player chooses among
strategies at random with fixed probabilities. In rock-paper-scissors the equilibrium is for each player to choose each
of the three with probability one third. A player who follows it cannot be exploited, although they cannot exploit an
opponent's patterns either.

The proof of Nash's theorem uses a **fixed-point theorem**, first Kakutani's and, in the version published in the
Annals of Mathematics in 1951, Brouwer's. The best responses of all players together define a map from combinations of
mixed strategies to combinations of mixed strategies, and an equilibrium is a point that the map sends to itself. When
Nash showed an early version of the argument to John von Neumann, von Neumann is reported to have said, "That's
trivial, you know. That's just a fixed-point theorem." The story is often told to show how unimpressed the older man
was, but it also shows how quickly the right tool made the problem tractable.

Equilibrium is a claim about stability, not about how players get there or whether they will. A Nash equilibrium is a
situation in which no one has a reason to move once they are in it. Experiments show that people often do not play
equilibrium strategies the first time they meet a game, especially when the reasoning needed to find the equilibrium
is long. With repetition and feedback, play in many games moves towards an equilibrium, which is one reason the idea
is used to predict the behaviour of firms in markets that have been running for years.

There are several **refinements** of the Nash equilibrium for games where it predicts too much. In sequential games a
Nash equilibrium can rest on threats that a player would never actually carry out. A shop owner might threaten to start
a ruinous price war if a competitor opens next door, and the competitor staying out together with the threat is a Nash
equilibrium. But once the competitor has opened, the price war would hurt the owner too, so the threat is not
**credible**. A **subgame perfect equilibrium**, introduced by Reinhard Selten in 1965, rules out such threats by
requiring the strategies to form an equilibrium in every subgame, that is, from every point of the game tree onwards.
Subgame perfect equilibria are found by **backward induction**: solve the last moves first, then the moves before them,
and so on back to the start.

My uncle, the noughts-and-crosses player, used backward induction long before I knew the word. When I was older he
taught me to play a game with matchsticks in which two players take turns removing one, two or three matches from a
pile of twenty-one, and whoever takes the last match loses. He would let me move first and still win every time, until
he showed me that he was simply leaving me a number of matches that was one more than a multiple of four. He said the
trick had been taught to him by a sailor on a cargo ship from Gdańsk, although with my uncle one could never be sure.

Nash shared the Nobel Memorial Prize in Economic Sciences in 1994 with John Harsanyi and Reinhard Selten, for their
analysis of equilibria in non-cooperative games. Harsanyi's contribution was to extend the idea to games with
**incomplete information**, in which players are uncertain about one another's payoffs, by treating each player's
private information as their **type** and assuming that the probabilities of the types are common knowledge. The
resulting **Bayesian Nash equilibrium** is the basis of the modern theory of auctions.

### 1.6 Cooperation in repeated games

When the same game is played many times, cooperation can be sustained even where a single round would not support it.
In a **repeated prisoner's dilemma**, a player can reward cooperation and punish betrayal in later rounds, which makes
cooperating worthwhile. In the 1980s Robert Axelrod invited researchers to submit computer programs to play a repeated
prisoner's dilemma against one another. The winner was the shortest program entered, **tit for tat**, which cooperates
in the first round and then copies whatever the opponent did in the previous round. Axelrod concluded that successful
strategies were nice (never the first to betray), retaliatory, forgiving and clear.

## 2. Mixed strategies in practice

### 2.1 Penalty kicks

A penalty kick in football is close to a simultaneous game: the kicker chooses a side, and the goalkeeper must choose
a side to dive to before the ball's direction can be seen. Studies of thousands of professional penalties have found
that kickers and goalkeepers choose sides with frequencies close to those that the mixed-strategy equilibrium predicts,
and that the scoring rates for each side are nearly equal, as the theory requires.

### 2.2 Inspections

Tax authorities, ticket inspectors and auditors face the same problem from the other side. If inspections were
certain, no one would cheat and the inspections would be wasted; if they were never made, everyone would cheat. In
equilibrium inspections are random, and their probability is set by the cost of inspecting and the gain from cheating,
not by how many people actually cheat.
