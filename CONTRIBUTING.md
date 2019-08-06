# How to contribute

ConnectBot is maintained by a small number of people and we don't have access to the device models that everyone may have. We want your help in solving issues that make ConnectBot a better client. Here are a few guidelines that we ask contributors to follow.

## Getting started

* Make sure you have a [GitHub account](https://github.com/signup/free)
* [Open an issue](https://github.com/connectbot/connectbot/issues) if one doesn't already exist
* Fork the repository on GitHub and then clone:
  * `git clone git@github.com:your-username/connectbot.git`
* Try to build for the first time:
  * `./gradlew assemble`
* Run the tests:
  * `./gradlew test`

## Making changes

* Create a topic branch from where you want to base your work.
  * This should be based off the master branch.
  * To create a topic branch based on master:
    * `git checkout -b my_fix master`
  * Make commits of logical units
  * Make sure your commit messages are in the proper format (from [Pro Git chapter 5.2](https://git-scm.com/book/en/v2/Distributed-Git-Contributing-to-a-Project)).
````
    Short (50 chars or less) summary of changes

    More detailed explanatory text, if necessary.  Wrap it to
    about 72 characters or so.  In some contexts, the first
    line is treated as the subject of an email and the rest of
    the text as the body.  The blank line separating the
    summary from the body is critical (unless you omit the body
    entirely); tools like rebase can get confused if you run
    the two together.

    Further paragraphs come after blank lines.

        - Bullet points are okay, too

        - Typically a hyphen or asterisk is used for the bullet,
          preceded by a single space, with blank lines in
          between, but conventions vary here
````
  * Make sure you have added necessary tests to your changes.
  * Check for unnecessary whitespace:
    * `git diff --check`
  * Make sure no new Android lint issues pop up:
    * `./gradlew lint`
    * Read the output to see if any of your newly-added or changed lines have lint errors.
  * Make sure all the checks and tests pass:
    * `./gradlew check test`

## Submitting changes

* Push your changes to a topic branch in your fork of the repository.
* Start a [pull request](https://github.com/connectbot/connectbot/compare/) for ConnectBot.

## Additional resources

* [#connectbot IRC channel](http://webchat.freenode.net/?channels=%23connectbot&uio=OT10cnVlJjExPTIwNQa5) on [Freenode](https://freenode.net/).
