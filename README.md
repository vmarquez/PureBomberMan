#Puregame


##A pure, functional, concurrent implementaiton of the game "Bomber Man"

When I first started studying and dabbling in functional programming, I quickly understood how pure functions could help reusibility, reduce complexity, and ovearll
be more pleasant to work with.  However, I did not immediately see how you could utilize functional progarmming in a state heavy, concurrent environment, and this
lack of knowledge seems to have turned into misinformation on various forums and message boards.  In an effort to answer the 'how' that a few friends have asked, I'd 
like to show how not only is it doable, it's incredibly simple and elegant using even some of the most basic functional concepts.  This is just one of many different
ways to write a concurrent, pure, functional game.   Before I scare any of you off, I'd like to say that until recently I've only programmed in imperative languages,
I have no formal computer science training, no math background, and I don't actually know category theory.  

I'm going to go step by step through a very naieve, conceptually simle implementation of bomber man in scala, so to follow you should have some familiarity with scala
and understand more or less what for comprehension is doing. 

A note about purity: 

First things first, we should define our 'entities', the data we'll be working with. 

```scala
    case class PlayerStats(bombsThrown: Int, wounds: Int, playersHit: Int)
    
    case class Player(name: String, callback: String, position: Int, stats: PlayerStats)
    
    //there are better datastructures for what we are doing here, 
    //but now this is conceptually pretty simple

    case class GameBoard(players: Map[String, Player], bombs: Map[Int, Boolean])

```

Nothing surprising here, we have a player, a group of player statistics, and a way to represent all of the active players along with
where there are currently bombs.  This will represent the world view of the game.  Because we're using immutable data, we need a way to 
update the state of the world. 

##Lenses

Scala gives us free 'copy' methods for each case class, but we'd end up needing to do a lot of this
```scala
    gameBoard.copy(players = gameBoard.players.updated("playername", 
        gameboard.players(""playername").copy(stats = player.playerStats.copy(wounds = player.playerStats.wounds + 1))))
```

Which is soul crushing to write.  Instead we'll use something called a Lens.  These are in ScalaZ (and there are a few other libraries), but 
for simplicities' sake I'll actually write my own implementation.  First, a lens is simply a datastructure consisting of a "getter" and "setter".

```scala
    case class VLens[A, B](set: (A, B) => A, get: A => B)
```

So, now we need to instantiate lens instances for our PlayerStats entity.  Our bomb lens takes a PlayerStats instance, an Int, and returns a new PlayerStats
instance.  Seems like a lot of boilerplate for no reason... 

    val bombLens = VLens.lensu[PlayerStats, Int]((stats, thrown) => stats.copy(bombsThrown = thrown), _.bombsThrown)
    val bombLens = VLens.lensu[PlayerStats, Int]((stats, thrown) => stats.copy(bombsThrown = thrown), _.bombsThrown)
    val woundLens = VLens.lensu[PlayerStats, Int]((stats, nw) => stats.copy(wounds = nw), _.wounds)
    val hitLens = VLens.lensu[PlayerStats, Int]((stats, nh) => stats.copy(playersHit = nh), _.playersHit)

We also need a way to set the PlayerStats entity *on* the Player entity, so 
    
    val statsLens = VLens.lensu[Player, PlayerStats]((player, ns) => player.copy(stats = ns), _.stats)

So how do we combine them? Ok I lied, VLens actually looks like this:

    case class VLens[A, B](set: (A, B) => A, get: A => B) {
        def andThen[C](otherLens: VLens[B, C]): VLens[A, C] = {
            VLens[A, C]((a, c) => {
                set(a, otherLens.set(get(a), c))
            }, (a) => otherLens.get(get(a)))
        }
    }

Now we can compose our two lenses to create a Lens so we can pass in a A (Player, in our case), and a C (an Int, for bombLens as an example), and return a new player. 

```scala
    val woundStatsLens = statsLens.andThen(woundLens)
    val hitStatsLens = statsLens.andThen(hitLens)
    val bombStatsLens = statsLens.andThen(bombLens)
```

Ok, we're making progress, but now how do we set a player (and subsequently, and of its relevant lenses) on a GameBoard? There are a few suggested ways, but I think it's
fine to have our lens be a function that takes a specific player.  Then we can continue composing our lenses


```scala
    val playerLens = (k: String) => VLens.lensu[GameBoard, Player]((gb, pl) => gb.copy(players = gb.players.updated(k, pl)), _.players(k))
    val bombStatsPlayerLens = (k: String) => (playerLens(k).andThen(bombStatsLens))
```
etc.





##Actions

Ok, now let's come up with various actions a player can take.

```scala
  sealed trait Action

  case class Join(name: String, callback: String) extends Action
  case class Move(name: String, position: Int) extends Action
  case class BombPlaced(name: String, position: Int) extends Action //name is the user who placed it
  case class BombExplodes(name: String, position: Int) extends Action //name is the user who placed it
```


Again, very straight forward stuff.  Reacting to actions such as Joining, Moving, and placing a bomb is just going to be calling into our lenses with the world state.
We need a bit more logic to determine our 'blast radious' for when a bomb finally explodes, so I just came up with something quick.  

```scala
  def getHitPlayers(p: Int, board: GameBoard): List[Player] = {
    val blastArea = getBlastArea(p, 10)
    board.players
      .map(_._2)
      .filter(p => blastArea.contains(p.position))
      .toList

  }

  def getUntilEdge(pos: Int, size: Int, ctr: Int, chk: (Int, Int) => Int, f: (Int, Int) => Int): List[Int] =
    if (chk(pos, ctr) % size == 0)
      List[Int]()
    else
      List[Int](f(pos, ctr)) ::: getUntilEdge(pos, size, ctr + 1, chk, f)

  def getBlastArea(pos: Int, size: Int): List[Int] = {
    val right = getUntilEdge(pos, size, 0, (_ + _), (_ + _))
    val left = getUntilEdge(pos, size, 0, (_ - _), (_ - _))
    val up = getUntilEdge(pos, size, 0, (_ + _), (a, b) => a + (b * size))
    val down = getUntilEdge(pos, size, 0, (_ - _), (a, b) => a - (b * size))
    right ::: left ::: up ::: down
  }
```

So how does our app work in aggregate?  Basically, we're going to get 'actions' coming in, update the global world state, and push
the global world state out to any players.  

```scala
    var world = GameBoard(players = Map[String,Player](), bombs=Map[Int, Boolean]()) //global in our example
```

We could code the 'move' action handler to look like the following

```scala
    case m: Move=> world = positionPlayerLens(m.name).set(world, m.position)

Which is fairly similar to how we're actually doing it, excep there's a problem.  Our game is concurrent. What happens if the world variable representing our Game State was
updated by another thread between reading it and modifing it?  We may have just wiped out someone else's changes, or vice versa.  Let's eliminate our global 'var' by replacing
it with the indominatable java.util.atomic.AtomicReference.  

```scala

    val worldRef = AtomicReference[GameBoard]()

    def handleMoveAtomic(m: Move): Unit = {
       val w = worldRef.get()
       val result = positionPlayerLens(m.name).set(w, m.position)
       if (!worldRef.compareAndSet(w, result)) //trying to atomically update
            handleMoveAtomic(m) //didn't work, so we'll try again
    }
```

So we have a way to ensure our update to the world state doesn't overwrite someone else's updates.  However, consider the case where we want to update mutiple times. 
An example of this is when we increment one player's wound count, and correspondingly increment the scoring player's hit count. 

```scala
    world = woundStatsPlayerLens("bob").set(world, 1)
    world = hitStatsPlayerLens("alice").set(world, 1) 
```
What we need is transactional semantics for a set of actions.  We could try and code a bunch of methods interwoven with AtomicReference compare and sets, but that's 
pretty awful and most likely error prone. 


##State Monad
First, let's talk a bit about some magic called the State Monad.
Instead of going on about mexican food, I'll just show the code, implore you to read it over a *lot*, (if you are unfamiliar), and
then send you off to http://blog.tmorris.net/posts/the-state-monad-for-scala-users/index.html. This is also excellently covered in 
chapter six of Functional Programming in Scala.

```scala
    case class State[A,B](run: A=>(A,B)) {
        def map[C](fmap: B=>C) = 
            State[A,C](a => {
                val (na,b) = run(a)
                val c = fmap(b)
                (na,c)
            })

        def flatMap[C](fmap: B=>State[A,C]) = 
            State[A,C](a => {
                val (na,b) = run(a)
                fmap(b).run(na)
            })
    }
```


So, why is the State Monad so powerful?   Because it allows us to compose multiple state changes into a single state transition.  There
are plenty of ways to compsose functions or actions, but State has the additional benefit of the fact that you can get a State from a Lens.


So, again, there were more omssisions regarding my Lens implementation. There's one more method (and an alias) 

```scala
  def mods(b: B) = State[A, B](a => {
    (set(a, b), b)
  })

  def :=(b: B) = mods(b)
```



