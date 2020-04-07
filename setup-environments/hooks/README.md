# estafet-openshift-boost git hooks

A commit-msg git hook that checks whether the commit message contains a valid trello link and that the card's status != Done

### Requirements

* **On Windows:** Git
* **On Linux:** Curl

> Note: This has currently only been tested on Windows

### How to use
1.
* If you have checked out the top level project - e.g. *estafet-blockchain-demo* then copy the file commit-msg to the *git\estafet-blockchain-demo\.git\modules\sub_project_name\hooks* folder where *sub_project_name* is the name of the sub-project you will be commiting to and want the validation to work on
* If you have checked out a sub-project directly then copy the file commit-msg to the folder *git\sub_project_name\.git\hooks* where *sub_project_name* is the name of the sub-project you have checked out to work on and want validation on your commits
2. 
 * **On Windows:** Make sure the first line of thepoints to the *sh.exe* file included with Git. E.g. #!C:/Program\ Files/Git/usr/bin/sh.exe 
 * **On Linux:** Change the top line to #!/bin/sh
 3.
 * Commit via command line with a commit message containing a trello link E.g. ``` git commit -m "https://trello.com/c/8M8wss8T This is my commit message" ```
 > Note: It may not work when commiting via an IDE
