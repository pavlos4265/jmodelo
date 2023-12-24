## jmodelo
Jmodelo is a mvc framework written in Java

To use it, include it in your project and create a package where your controller classes will reside.
You will also need to create 4 folders: views, temp (where uploaded files will be temporarily stored), sessions and www (where your static files will be).

Your controllers will need to have the "Controller" suffix and be compiled with the -parameters flag so the parameter names are included
in the class files. The controllers will also need to extend the Controller class which includes helpful methods to redirect, return views, json, etc

The urls are parsed like this:
- /{Area}/{Controller}/{Method}
- /{Area}/{Controller} -> Calls the index method of the controller (of this area)
- /{Area}/{Method} -> Calls the method of the DefaultController (of this area)
- /{Area} -> Calls the index method of the DefaultController (of this area)
- /{Controller}/{Method}
- /{Controller} -> Calls the index method of the controller
- /{Method} -> Calls the method of the DefaultController
- / -> Calls the index method of the DefaultController

You can also pass parameters in the path (example: /1/Test). To match them with your controller's method parameters use the custom UrlArg(index) annotation.
Parameters from the usual url queries (?id=1&name=Test) are automatically matched to your method parameters. You can also access the query values
through a GetParams object. In order to process post requests you have to add the HttpPost annonation to your method and access the values or files
through a PostData object.

The views need to be included inside a folder that is named after the controller. For example, the views of the DefaultController need 
to exist inside of views/Default/

The scripting language of the views is Javascript and is placed inside of <%%>. Example:
```
	<html>
		<body>
		<% for(var i = 0; i < 10; i++) { %>
			<b>test <%_s(i);%></b><br/>
		<% } %>
		</body>
	</html>
```

The _s() function is used to display the variable. To include a view inside another view use the _partial(view, model) function. The views
are converted to javascript and are evaluated by the Nashorn javascript engine.

For the database connections HikariCP is used. To initialize it, pass a HikariConfig object with the proper information to the MVC constructor.
You can the use a connection from inside your controller using the inherited getDatabaseConnection() method.

To start the webserver, use the methods in the MVC class. Requires Java 21.
